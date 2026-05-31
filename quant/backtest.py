"""回测引擎：策略基类、运行器、交易模拟、绩效计算"""

from __future__ import annotations

import json
import math
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional

import pandas as pd
import numpy as np

from db import get_connection


# ---------- 数据模型 ----------

@dataclass
class Trade:
    """单笔交易记录"""
    date: str
    direction: str  # 'buy' | 'sell'
    price: float     # 成交净值
    shares: float    # 成交份额
    amount: float    # 成交金额
    fee: float       # 手续费


@dataclass
class EquityPoint:
    """净值曲线上的一个点"""
    date: str
    nav: float
    total_value: float    # 持仓总市值
    invested: float       # 累计投入
    shares: float         # 持仓份额


@dataclass
class BacktestResult:
    """回测结果"""
    strategy: str
    fund_code: str
    params: dict
    start_date: str
    end_date: str

    total_return: float      # 总收益率 %
    annual_return: float     # 年化收益率 %
    max_drawdown: float      # 最大回撤 %
    sharpe: float            # 夏普比率
    win_rate: float          # 胜率 %
    trade_count: int         # 交易次数

    trades: list[Trade] = field(default_factory=list)
    equity_curve: list[EquityPoint] = field(default_factory=list)

    def to_dict(self):
        return {
            "strategy": self.strategy,
            "fund_code": self.fund_code,
            "params": json.dumps(self.params, ensure_ascii=False),
            "start_date": self.start_date,
            "end_date": self.end_date,
            "total_return": round(self.total_return, 2),
            "annual_return": round(self.annual_return, 2),
            "max_drawdown": round(self.max_drawdown, 2),
            "sharpe": round(self.sharpe, 2),
            "win_rate": round(self.win_rate, 2),
            "trade_count": self.trade_count,
        }


# ---------- 策略基类 ----------

class Strategy(ABC):
    """策略基类，所有策略需实现 on_bar 方法"""

    name: str = "base"
    params: dict = {}

    def __init__(self, **kwargs):
        self.params = {**self.params, **kwargs}
        self._nav_data: pd.DataFrame | None = None
        self._bar_index: int = 0

    def on_init(self, nav_data: pd.DataFrame):
        """策略初始化，nav_data 为完整的历史净值数据
        可在子类中重写，用于预计算指标
        """
        self._nav_data = nav_data
        self._bar_index = 0

    @abstractmethod
    def on_bar(self, row: pd.Series) -> Optional[str] | tuple:
        """每根 K 线调用
        返回信号:
          'buy' / 'sell'          → 全仓买卖
          ('buy', 金额)           → 指定金额买入
          ('sell', 份额)          → 指定份额卖出
          None                    → 无操作
        """
        ...

    def on_finish(self):
        """回测结束回调"""
        pass

    @property
    def progress(self) -> float:
        """回测进度 0~1"""
        if self._nav_data is None or len(self._nav_data) == 0:
            return 0
        return self._bar_index / len(self._nav_data)


# ---------- 回测运行器 ----------

class BacktestRunner:
    """回测运行器"""

    def __init__(
        self,
        strategy: Strategy,
        buy_fee_rate: float = 0.0015,   # 申购费率 0.15%
        sell_fee_rate: float = 0.005,   # 赎回费率 0.5%
        risk_free_rate: float = 0.02,   # 无风险利率 2%
        initial_cash: float = 1_000_000,  # 初始资金（用于仓位计算）
    ):
        self.strategy = strategy
        self.buy_fee_rate = buy_fee_rate
        self.sell_fee_rate = sell_fee_rate
        self.risk_free_rate = risk_free_rate
        self.initial_cash = initial_cash

    def run(
        self,
        fund_code: str,
        start_date: str | None = None,
        end_date: str | None = None,
        data_source: str = "nav",   # "nav" | "kline"
    ) -> BacktestResult:
        """执行回测"""
        if data_source == "kline":
            nav_data = self._load_kline_data(fund_code, start_date, end_date)
        else:
            nav_data = self._load_nav_data(fund_code, start_date, end_date)
        if nav_data.empty:
            raise ValueError(f"基金 {fund_code} 在 {start_date}~{end_date} 无净值数据")

        start = str(nav_data["date"].iloc[0].date()) if hasattr(nav_data["date"].iloc[0], "date") else str(nav_data["date"].iloc[0])
        end = str(nav_data["date"].iloc[-1].date()) if hasattr(nav_data["date"].iloc[-1], "date") else str(nav_data["date"].iloc[-1])
        self.strategy.on_init(nav_data)
        self.strategy._bar_index = 0

        # 回测状态
        cash = self.initial_cash
        shares = 0.0
        trades: list[Trade] = []
        equity_curve: list[EquityPoint] = []

        # 逐日回放
        for i, (_, row) in enumerate(nav_data.iterrows()):
            self.strategy._bar_index = i
            nav = row["nav"]
            date = str(row["date"].date()) if hasattr(row["date"], "date") else str(row["date"])

            signal = self.strategy.on_bar(row)

            # 支持两种信号格式:
            #   "buy" / "sell"          → 全仓
            #   ("buy", amount)         → 指定金额买入
            #   ("sell", shares)        → 指定份额卖出
            if isinstance(signal, tuple):
                action, value = signal
            else:
                action = signal
                value = None

            if action == "buy":
                buy_cash = value if value is not None else cash
                buy_cash = min(buy_cash, cash)
                if buy_cash > 0:
                    fee = buy_cash * self.buy_fee_rate
                    buy_amount = buy_cash - fee
                    bought_shares = buy_amount / nav
                    shares += bought_shares
                    cash -= buy_cash
                    trades.append(Trade(
                        date=date, direction="buy", price=nav,
                        shares=bought_shares, amount=buy_cash, fee=fee,
                    ))
            elif action == "sell":
                sell_shares = value if value is not None else shares
                sell_shares = min(sell_shares, shares)
                if sell_shares > 0:
                    sell_amount = sell_shares * nav
                    fee = sell_amount * self.sell_fee_rate
                    cash += sell_amount - fee
                    shares -= sell_shares
                    trades.append(Trade(
                        date=date, direction="sell", price=nav,
                        shares=sell_shares, amount=sell_amount, fee=fee,
                    ))

            # 记录净值曲线
            total_value = cash + shares * nav
            equity_curve.append(EquityPoint(
                date=date, nav=nav,
                total_value=round(total_value, 2),
                invested=round(self.initial_cash - cash, 2),
                shares=round(shares, 4),
            ))

        self.strategy.on_finish()

        # 计算绩效
        metrics = self._calc_metrics(equity_curve, trades, nav_data)

        return BacktestResult(
            strategy=self.strategy.name,
            fund_code=fund_code,
            params=self.strategy.params,
            start_date=start,
            end_date=end,
            trades=trades,
            equity_curve=equity_curve,
            **metrics,
        )

    def _load_nav_data(
        self, code: str, start_date: str | None, end_date: str | None
    ) -> pd.DataFrame:
        """从数据库加载净值数据"""
        conn = get_connection()
        query = (
            "SELECT date, nav, acc_nav FROM nav_history "
            "WHERE code = ? ORDER BY date ASC"
        )
        params = [code]
        df = pd.read_sql_query(query, conn, params=params)
        conn.close()

        if df.empty:
            return df

        df["date"] = pd.to_datetime(df["date"])
        if start_date:
            df = df[df["date"] >= pd.to_datetime(start_date)]
        if end_date:
            df = df[df["date"] <= pd.to_datetime(end_date)]

        df = df.reset_index(drop=True)
        return df

    def _load_kline_data(
        self, code: str, start_date: str | None, end_date: str | None
    ) -> pd.DataFrame:
        """从数据库加载 K-line 数据，close 映射为 nav 以兼容策略"""
        code = self._normalize_code(code)
        conn = get_connection()
        query = (
            "SELECT date, close AS nav, open, high, low, volume "
            "FROM kline WHERE code = ? ORDER BY date ASC"
        )
        df = pd.read_sql_query(query, conn, params=[code])
        conn.close()

        if df.empty:
            return df

        df["date"] = pd.to_datetime(df["date"])
        if start_date:
            df = df[df["date"] >= pd.to_datetime(start_date)]
        if end_date:
            df = df[df["date"] <= pd.to_datetime(end_date)]

        df = df.reset_index(drop=True)
        return df

    @staticmethod
    def _normalize_code(code: str) -> str:
        """规范化代码: 510050 → sh510050"""
        code = code.strip()
        if code.startswith("sh") or code.startswith("sz"):
            return code
        # 上海: 6/5/9 开头, 深圳: 0/3/2 开头
        if code[0] in ("6", "5", "9"):
            return f"sh{code}"
        return f"sz{code}"

    def _calc_metrics(
        self,
        equity_curve: list[EquityPoint],
        trades: list[Trade],
        nav_data: pd.DataFrame,
    ) -> dict:
        """计算绩效指标"""
        if not equity_curve:
            return {
                "total_return": 0, "annual_return": 0, "max_drawdown": 0,
                "sharpe": 0, "win_rate": 0, "trade_count": 0,
            }

        final_value = equity_curve[-1].total_value
        # 实际投入 = 初始资金（网格/动量等策略会反复买卖，累计买入额不代表实际投入）
        invested = self.initial_cash

        # 总收益率
        total_return = (final_value - invested) / invested * 100

        # 交易日数
        trading_days = len(equity_curve)

        # 年化收益率
        if trading_days > 0:
            annual_return = (
                (1 + total_return / 100) ** (250 / trading_days) - 1
            ) * 100
        else:
            annual_return = 0

        # 最大回撤
        values = [e.total_value for e in equity_curve]
        peak = values[0]
        max_dd = 0
        for v in values:
            if v > peak:
                peak = v
            dd = (peak - v) / peak * 100
            if dd > max_dd:
                max_dd = dd

        # 夏普比率
        if trading_days > 1:
            daily_returns = [
                (values[i] - values[i - 1]) / values[i - 1]
                for i in range(1, len(values))
            ]
            avg_daily_return = np.mean(daily_returns)
            std_daily_return = np.std(daily_returns, ddof=1)
            if std_daily_return > 0:
                daily_rf = self.risk_free_rate / 250
                sharpe = (
                    (avg_daily_return - daily_rf) / std_daily_return
                    * math.sqrt(250)
                )
            else:
                sharpe = 0
        else:
            sharpe = 0

        # 胜率：盈利交易次数 / 总交易次数
        # 统计买入-卖出对的盈亏
        buy_trades = [t for t in trades if t.direction == "buy"]
        sell_trades = [t for t in trades if t.direction == "sell"]
        win_count = 0
        for sell in sell_trades:
            # 找对应的买入（简化：按顺序配对）
            profit = sell.amount - sell.shares * sell.price  # 净卖出金额 vs 持有成本
            if profit > 0:
                win_count += 1

        total_trade_pairs = min(len(buy_trades), len(sell_trades))
        win_rate = (win_count / total_trade_pairs * 100) if total_trade_pairs > 0 else 0

        return {
            "total_return": round(total_return, 2),
            "annual_return": round(annual_return, 2),
            "max_drawdown": round(max_dd, 2),
            "sharpe": round(sharpe, 2),
            "win_rate": round(win_rate, 2),
            "trade_count": len(trades),
        }


# ---------- 买入持有基准 ----------

class BuyHoldStrategy(Strategy):
    """买入持有策略（基准）"""
    name = "买入持有"

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._bought = False

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        self._bought = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        if not self._bought:
            self._bought = True
            return "buy"
        return None


def run_backtest_and_save(
    fund_code: str,
    strategy: Strategy,
    start_date: str | None = None,
    end_date: str | None = None,
    save: bool = True,
) -> BacktestResult:
    """运行回测并可选保存结果到数据库"""
    runner = BacktestRunner(strategy)
    result = runner.run(fund_code, start_date, end_date)

    if save:
        conn = get_connection()
        conn.execute(
            """INSERT INTO backtest_results
               (strategy, fund_code, params, start_date, end_date,
                total_return, annual_return, max_drawdown, sharpe, win_rate, trade_count)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                result.strategy, result.fund_code,
                json.dumps(result.params, ensure_ascii=False),
                result.start_date, result.end_date,
                result.total_return, result.annual_return,
                result.max_drawdown, result.sharpe,
                result.win_rate, result.trade_count,
            ),
        )
        conn.commit()
        conn.close()

    return result


if __name__ == "__main__":
    # 简单测试
    bh = BuyHoldStrategy()
    result = run_backtest_and_save("001856", bh, "2025-01-01", "2026-05-31")
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
    print(f"交易次数: {result.trade_count}")
    print(f"净值曲线点数: {len(result.equity_curve)}")
