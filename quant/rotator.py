"""动量轮动策略：多标的动量排名，定期调仓"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

import pandas as pd
import numpy as np

from db import get_connection
from backtest import BacktestResult


@dataclass
class RotationTrade:
    """轮动调仓记录"""
    date: str
    holdings: list[dict]  # [{code, direction, nav, amount}]


@dataclass
class RotationResult:
    """轮动回测结果"""
    codes: list[str]
    params: dict
    start_date: str
    end_date: str
    total_return: float
    annual_return: float
    max_drawdown: float
    sharpe: float
    rebalance_count: int
    trades: list[RotationTrade] = field(default_factory=list)
    equity_curve: list[dict] = field(default_factory=list)

    def to_dict(self):
        return {
            "strategy": "动量轮动",
            "codes": ",".join(self.codes),
            "params": json.dumps(self.params, ensure_ascii=False),
            "start_date": self.start_date,
            "end_date": self.end_date,
            "total_return": round(self.total_return, 2),
            "annual_return": round(self.annual_return, 2),
            "max_drawdown": round(self.max_drawdown, 2),
            "sharpe": round(self.sharpe, 2),
            "rebalance_count": self.rebalance_count,
        }


class RotationRunner:
    """动量轮动回测运行器

    在多个基金间轮动，每次调仓日计算 N 日涨幅，
    买入涨幅最大的前 K 只基金，平仓其余。
    """

    def __init__(
        self,
        lookback: int = 20,
        top_k: int = 3,
        rebalance_days: int = 20,
        buy_fee_rate: float = 0.0003,   # ETF 费率低
        sell_fee_rate: float = 0.0003,
        risk_free_rate: float = 0.02,
        initial_cash: float = 1_000_000,
        data_source: str = "nav",   # "nav" | "kline"
    ):
        self.lookback = lookback
        self.top_k = top_k
        self.rebalance_days = rebalance_days
        self.buy_fee_rate = buy_fee_rate
        self.sell_fee_rate = sell_fee_rate
        self.risk_free_rate = risk_free_rate
        self.initial_cash = initial_cash
        self.data_source = data_source

    def _normalize_code(self, code: str) -> str:
        code = code.strip()
        if code.startswith("sh") or code.startswith("sz"):
            return code
        if code[0] in ("6", "5", "9"):
            return f"sh{code}"
        return f"sz{code}"

    def run(
        self,
        codes: list[str],
        start_date: str | None = None,
        end_date: str | None = None,
    ) -> RotationResult:
        """执行轮动回测"""
        # 加载多基金数据
        all_data = {}
        common_dates = None

        for code in codes:
            df = self._load_nav(code, start_date, end_date)
            if df.empty:
                print(f"  ⚠ {code}: 无数据，跳过")
                continue
            all_data[code] = df.set_index("date")["nav"]
            if common_dates is None:
                common_dates = set(df["date"].astype(str))
            else:
                common_dates &= set(df["date"].astype(str))

        if not all_data or not common_dates:
            raise ValueError("无可用数据")

        # 构建对齐后的 DataFrame
        common_dates = sorted(common_dates)
        nav_df = pd.DataFrame({code: s.reindex(common_dates) for code, s in all_data.items()})
        nav_df.index = pd.to_datetime(common_dates)
        nav_df = nav_df.dropna(axis=1, how="all").ffill().dropna()

        if nav_df.empty:
            raise ValueError("对齐后无数据")

        # 计算动量
        momentum = nav_df.pct_change(self.lookback)

        # 回测
        dates = nav_df.index.tolist()
        cash = self.initial_cash
        holdings: dict[str, float] = {}  # code → shares
        trades: list[RotationTrade] = []
        equity_curve: list[dict] = []

        day_count = 0

        for i, date in enumerate(dates):
            date_str = str(date.date())
            nav_today = nav_df.iloc[i]

            # 判断是否为调仓日
            should_rebalance = False
            if day_count == 0:
                should_rebalance = True  # 首个交易日调仓
            elif day_count >= self.rebalance_days:
                should_rebalance = True

            if should_rebalance and i >= self.lookback:
                day_count = 0
                # 计算当前持仓市值
                holding_value = sum(
                    holdings.get(code, 0) * nav_today[code]
                    for code in holdings
                ) if holdings else 0
                total_value = cash + holding_value

                # 按动量排序
                mom_series = momentum.iloc[i].dropna().sort_values(ascending=False)
                top_codes = mom_series.head(self.top_k).index.tolist()

                # 清仓不在 top_k 的持仓
                trades_today = []
                for code in list(holdings.keys()):
                    if code not in top_codes:
                        shares = holdings.pop(code)
                        amount = shares * nav_today[code]
                        fee = amount * self.sell_fee_rate
                        cash += amount - fee
                        trades_today.append({
                            "code": code, "direction": "sell",
                            "nav": round(nav_today[code], 4),
                            "amount": round(amount, 2),
                        })

                # 等权买入 top_k 基金
                if top_codes and cash > 0:
                    amount_per = cash / len(top_codes)
                    for code in top_codes:
                        fee = amount_per * self.buy_fee_rate
                        buy_amount = amount_per - fee
                        shares_bought = buy_amount / nav_today[code]
                        holdings[code] = holdings.get(code, 0) + shares_bought
                        cash -= amount_per
                        trades_today.append({
                            "code": code, "direction": "buy",
                            "nav": round(nav_today[code], 4),
                            "amount": round(amount_per, 2),
                        })

                if trades_today:
                    trades.append(RotationTrade(
                        date=date_str, holdings=trades_today
                    ))

            # 更新净值曲线
            holding_value = sum(
                holdings.get(code, 0) * nav_today[code]
                for code in holdings
            ) if holdings else 0
            total_value = cash + holding_value
            equity_curve.append({
                "date": date_str,
                "total_value": round(total_value, 2),
                "invested": round(self.initial_cash, 2),
                "holding_count": len(holdings),
            })

            day_count += 1

        # 计算绩效
        metrics = self._calc_metrics(equity_curve, dates, nav_df)

        return RotationResult(
            codes=codes,
            params={"lookback": self.lookback, "top_k": self.top_k, "rebalance_days": self.rebalance_days},
            start_date=str(dates[0].date()),
            end_date=str(dates[-1].date()),
            trades=trades,
            equity_curve=equity_curve,
            rebalance_count=len(trades),
            **metrics,
        )

    def _load_nav(self, code: str, start_date, end_date) -> pd.DataFrame:
        conn = get_connection()
        if self.data_source == "kline":
            norm = self._normalize_code(code)
            query = "SELECT date, close AS nav FROM kline WHERE code = ? ORDER BY date ASC"
            df = pd.read_sql_query(query, conn, params=[norm])
        else:
            query = "SELECT date, nav FROM nav_history WHERE code = ? ORDER BY date ASC"
            df = pd.read_sql_query(query, conn, params=[code])
        conn.close()

        if df.empty:
            return df

        df["date"] = pd.to_datetime(df["date"])
        if start_date:
            df = df[df["date"] >= pd.to_datetime(start_date)]
        if end_date:
            df = df[df["date"] <= pd.to_datetime(end_date)]
        return df

    def _calc_metrics(self, equity_curve, dates, nav_df) -> dict:
        if not equity_curve:
            return {"total_return": 0, "annual_return": 0, "max_drawdown": 0, "sharpe": 0}

        final_value = equity_curve[-1]["total_value"]
        invested = self.initial_cash
        total_return = (final_value - invested) / invested * 100

        trading_days = len(equity_curve)
        annual_return = ((1 + total_return / 100) ** (250 / max(trading_days, 1)) - 1) * 100

        values = [e["total_value"] for e in equity_curve]
        peak = values[0]
        max_dd = 0
        for v in values:
            if v > peak:
                peak = v
            dd = (peak - v) / peak * 100
            if dd > max_dd:
                max_dd = dd

        sharpe = 0
        if trading_days > 1:
            daily_returns = [(values[i] - values[i-1]) / values[i-1] for i in range(1, len(values))]
            std = np.std(daily_returns, ddof=1)
            if std > 0:
                sharpe = (np.mean(daily_returns) - self.risk_free_rate/250) / std * np.sqrt(250)

        return {
            "total_return": round(total_return, 2),
            "annual_return": round(annual_return, 2),
            "max_drawdown": round(max_dd, 2),
            "sharpe": round(sharpe, 2),
        }


def run_rotation(
    codes: list[str],
    lookback: int = 20,
    top_k: int = 3,
    rebalance_days: int = 20,
    start_date: str | None = None,
    end_date: str | None = None,
) -> RotationResult:
    """快捷运行轮动回测"""
    runner = RotationRunner(
        lookback=lookback, top_k=top_k, rebalance_days=rebalance_days
    )
    return runner.run(codes, start_date, end_date)


def print_rotation_report(result: RotationResult, show_chart: bool = True):
    """输出轮动回测报告"""
    sep = "=" * 56
    print(sep)
    print(f"  轮动回测报告: 动量轮动")
    print(f"  标的: {', '.join(result.codes)}")
    print(sep)
    print(f"  回测区间:  {result.start_date} → {result.end_date}")
    print(f"  策略参数:  lookback={result.params['lookback']}, "
          f"top_k={result.params['top_k']}, "
          f"rebalance={result.params['rebalance_days']}天")
    print(f"  调仓次数:  {result.rebalance_count}")
    print()
    print(f"  指标           数值")
    print(f"  {'─' * 16} {'─' * 10}")
    print(f"  总收益率        {result.total_return:+.2f}%")
    print(f"  年化收益率      {result.annual_return:+.2f}%")
    print(f"  最大回撤        {result.max_drawdown:.2f}%")
    print(f"  夏普比率        {result.sharpe:.2f}")
    print()

    if result.trades:
        for t in result.trades[:5]:
            codes_str = ", ".join(
                f"{h['code']} {'买入' if h['direction']=='buy' else '卖出'}"
                for h in t.holdings[:3]
            )
            print(f"  {t.date}: {codes_str}{'…' if len(t.holdings) > 3 else ''}")
        if len(result.trades) > 5:
            print(f"  ... 共 {result.rebalance_count} 次调仓")
        print()

    print(sep)

    if show_chart:
        _plot_rotation_chart(result)


def _plot_rotation_chart(result: RotationResult):
    try:
        import matplotlib
        matplotlib.rcParams["font.sans-serif"] = ["PingFang SC", "Heiti TC", "SimHei"]
        matplotlib.rcParams["axes.unicode_minus"] = False
        import matplotlib.pyplot as plt

        os.makedirs("charts", exist_ok=True)

        curve = result.equity_curve
        dates = [c["date"] for c in curve]
        values = [c["total_value"] for c in curve]

        fig, ax = plt.subplots(figsize=(12, 5))
        x = range(len(dates))
        ax.plot(x, values, color="#FF5722", linewidth=1.5)
        ax.set_ylabel("市值")
        ax.set_title(f"动量轮动 — {', '.join(result.codes)}")
        ax.grid(True, alpha=0.3)

        tick_step = max(1, len(dates) // 8)
        tick_pos = list(range(0, len(dates), tick_step))
        ax.set_xticks(tick_pos)
        ax.set_xticklabels([dates[i] for i in tick_pos], rotation=30, ha="right")

        plt.tight_layout()
        filename = f"rotation_{','.join(result.codes[:2])}.png"
        filepath = os.path.join(os.path.expanduser("~/.ohmyyangjibao/charts"), filename)
        plt.savefig(filepath, dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"  图表已保存: {filepath}")
    except ImportError:
        pass
