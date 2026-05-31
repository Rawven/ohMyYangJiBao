"""内置策略：均线交叉、估值百分位、定投、网格、动量"""

from typing import Optional

import pandas as pd
import numpy as np

from backtest import Strategy, BuyHoldStrategy
from strategies_advanced import (
    MACDStrategy, RSIStrategy, BollingerStrategy, KDJStrategy,
    MARSIStrategy, MACDBollingerStrategy, TripleMAStrategy,
    TrailingStopStrategy, PyramidingStrategy,
)


class MaCrossStrategy(Strategy):
    """均线交叉策略

    短期均线上穿长期均线 → 买入
    短期均线下穿长期均线 → 卖出

    参数:
        short_win: 短期均线窗口（默认 5 日）
        long_win:  长期均线窗口（默认 20 日）
    """
    name = "均线交叉"
    params = {"short_win": 5, "long_win": 20}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        closes = nav_data["nav"]
        self._ma_short = closes.rolling(self.params["short_win"]).mean()
        self._ma_long = closes.rolling(self.params["long_win"]).mean()
        self._prev_diff = None
        self._bought = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < max(self.params["short_win"], self.params["long_win"]):
            return None

        short_val = self._ma_short.iloc[i]
        long_val = self._ma_long.iloc[i]
        current_diff = short_val - long_val

        if self._prev_diff is not None:
            # 上穿买入
            if self._prev_diff <= 0 and current_diff > 0:
                self._prev_diff = current_diff
                return "buy"
            # 下穿卖出
            if self._prev_diff >= 0 and current_diff < 0:
                self._prev_diff = current_diff
                return "sell"

        self._prev_diff = current_diff
        return None

    def on_finish(self):
        self._prev_diff = None


class ValuationPercentileStrategy(Strategy):
    """估值百分位策略

    基于历史净值计算当前净值在历史区间中的百分位：
      - 百分位 < pe_low → 低估，买入
      - 百分位 > pe_high → 高估，卖出

    参数:
        pe_low:  低估阈值（默认 20）
        pe_high: 高估阈值（默认 80）
        lookback_days: 回看天数（默认 252，即一年）
    """
    name = "估值百分位"
    params = {"pe_low": 20, "pe_high": 80, "lookback_days": 252}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        closes = nav_data["nav"].values
        self._percentiles = np.full(len(closes), 50.0)

        for i in range(len(closes)):
            start = max(0, i - self.params["lookback_days"])
            window = closes[start:i + 1]
            if len(window) < 2:
                self._percentiles[i] = 50.0
            else:
                val = closes[i]
                count_below = np.sum(window[:-1] < val)
                pct = count_below / (len(window) - 1) * 100
                self._percentiles[i] = pct

        self._prev_position = None

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["lookback_days"]:
            return None

        pct = self._percentiles[i]
        low = self.params["pe_low"]
        high = self.params["pe_high"]

        signal = None
        if pct < low:
            signal = "buy"
        elif pct > high:
            signal = "sell"

        # 避免重复信号
        if signal == self._prev_position:
            return None
        self._prev_position = signal
        return signal


class DripStrategy(Strategy):
    """定投策略

    每月固定日期买入固定金额（不卖出）

    参数:
        amount: 每次定投金额（默认 1000）
        day_of_month: 定投日期（默认 1，即每月 1 日）
    """
    name = "定投"
    params = {"amount": 1000, "day_of_month": 1}

    def on_bar(self, row: pd.Series) -> Optional[str | tuple]:
        date = row["date"]
        if hasattr(date, "strftime"):
            day = date.day
        else:
            day = pd.Timestamp(date).day

        target_day = self.params["day_of_month"]
        if day >= target_day:
            if self._bar_index > 0:
                prev_row = self._nav_data.iloc[self._bar_index - 1]
                prev_date = prev_row["date"]
                if hasattr(prev_date, "strftime"):
                    prev_day = prev_date.day
                else:
                    prev_day = pd.Timestamp(prev_date).day
                if prev_day >= target_day:
                    return None

            return ("buy", self.params["amount"])
        return None


class GridStrategy(Strategy):
    """网格策略

    在价格区间内等间距设置网格线，每次触发买卖一份

    参数:
        grid_low: 网格下界
        grid_high: 网格上界
        grid_count: 网格份数（默认 10）
    """
    name = "网格"
    params = {"grid_low": None, "grid_high": None, "grid_count": 10}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        low = self.params["grid_low"]
        high = self.params["grid_high"]

        if low is None:
            low = nav_data["nav"].min()
            self.params["grid_low"] = low
        if high is None:
            high = nav_data["nav"].max()
            self.params["grid_high"] = high

        self._grid_levels = np.linspace(low, high, self.params["grid_count"] + 1)
        self._prev_nav = None

    def on_bar(self, row: pd.Series) -> Optional[str]:
        nav = row["nav"]
        if self._prev_nav is None:
            self._prev_nav = nav
            return None

        prev_level = self._find_level(self._prev_nav)
        curr_level = self._find_level(nav)

        if curr_level < prev_level:
            # 价格跌穿一个网格 → 买入一份
            self._prev_nav = nav
            return "buy"
        elif curr_level > prev_level:
            # 价格涨穿一个网格 → 卖出一份
            self._prev_nav = nav
            return "sell"

        self._prev_nav = nav
        return None

    def _find_level(self, price: float) -> int:
        for i, level in enumerate(self._grid_levels):
            if price <= level:
                return i
        return len(self._grid_levels) - 1


class MomentumStrategy(Strategy):
    """动量策略

    过去 N 日涨幅为正 → 买入（持有时不重复买入）
    过去 N 日涨幅为负 → 卖出

    参数:
        lookback_days: 回看天数（默认 20）
    """
    name = "动量"
    params = {"lookback_days": 20}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        returns = nav_data["nav"].pct_change(self.params["lookback_days"])
        self._momentum = returns.fillna(0)
        self._position = None  # None=空仓, 'hold'=持仓

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["lookback_days"]:
            return None

        mom = self._momentum.iloc[i]
        signal = None

        if mom > 0 and self._position != "hold":
            signal = "buy"
            self._position = "hold"
        elif mom <= 0 and self._position == "hold":
            signal = "sell"
            self._position = None

        return signal

    def on_finish(self):
        self._position = None


# 策略注册表
STRATEGIES: dict[str, type[Strategy]] = {
    # 原有
    "买入持有": BuyHoldStrategy,
    "均线交叉": MaCrossStrategy,
    "估值百分位": ValuationPercentileStrategy,
    "定投": DripStrategy,
    "网格": GridStrategy,
    "动量": MomentumStrategy,
    # 经典技术指标
    "MACD": MACDStrategy,
    "RSI": RSIStrategy,
    "布林带": BollingerStrategy,
    "KDJ": KDJStrategy,
    # 组合信号
    "MA+RSI": MARSIStrategy,
    "MACD+布林带": MACDBollingerStrategy,
    "三均线": TripleMAStrategy,
    # 仓位管理
    "移动止盈止损": TrailingStopStrategy,
    "金字塔加仓": PyramidingStrategy,
}


def get_strategy(name: str, **params) -> Strategy:
    """按名称获取策略实例"""
    if name not in STRATEGIES:
        available = "、".join(STRATEGIES.keys())
        raise ValueError(f"未知策略: {name}，可用策略: {available}")
    return STRATEGIES[name](**params)
