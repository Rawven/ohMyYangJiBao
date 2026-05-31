"""高级策略：经典技术指标、组合信号、仓位管理"""

from typing import Optional

import pandas as pd
import numpy as np

from backtest import Strategy, BuyHoldStrategy


# ===================== 经典技术指标 =====================

class MACDStrategy(Strategy):
    """MACD 策略
    DIF 上穿 DEA → 买入，下穿 → 卖出
    参数: fast=12, slow=26, signal=9
    """
    name = "MACD"
    params = {"fast": 12, "slow": 26, "signal": 9}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        ema_fast = close.ewm(span=self.params["fast"], adjust=False).mean()
        ema_slow = close.ewm(span=self.params["slow"], adjust=False).mean()
        self._dif = ema_fast - ema_slow
        self._dea = self._dif.ewm(span=self.params["signal"], adjust=False).mean()
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        min_bars = self.params["slow"] + self.params["signal"]
        if i < min_bars:
            return None

        dif_prev = self._dif.iloc[i - 1]
        dea_prev = self._dea.iloc[i - 1]
        dif_cur = self._dif.iloc[i]
        dea_cur = self._dea.iloc[i]

        if dif_prev <= dea_prev and dif_cur > dea_cur and not self._in_position:
            self._in_position = True
            return "buy"
        if dif_prev >= dea_prev and dif_cur < dea_cur and self._in_position:
            self._in_position = False
            return "sell"
        return None

    def on_finish(self):
        self._in_position = False


class RSIStrategy(Strategy):
    """RSI 策略
    RSI < oversold → 买入，> overbought → 卖出
    参数: period=14, oversold=30, overbought=70
    """
    name = "RSI"
    params = {"period": 14, "oversold": 30, "overbought": 70}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        delta = close.diff()
        gain = delta.clip(lower=0)
        loss = (-delta).clip(lower=0)
        avg_gain = gain.rolling(self.params["period"], min_periods=self.params["period"]).mean()
        avg_loss = loss.rolling(self.params["period"], min_periods=self.params["period"]).mean()
        rs = avg_gain / avg_loss.replace(0, np.nan)
        self._rsi = 100 - (100 / (1 + rs))
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["period"]:
            return None

        rsi_val = self._rsi.iloc[i]
        if pd.isna(rsi_val):
            return None

        if rsi_val < self.params["oversold"] and not self._in_position:
            self._in_position = True
            return "buy"
        if rsi_val > self.params["overbought"] and self._in_position:
            self._in_position = False
            return "sell"
        return None

    def on_finish(self):
        self._in_position = False


class BollingerStrategy(Strategy):
    """布林带策略
    价格触及下轨 → 买入，触及上轨 → 卖出
    参数: period=20, std_dev=2
    """
    name = "布林带"
    params = {"period": 20, "std_dev": 2}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        self._ma = close.rolling(self.params["period"]).mean()
        self._std = close.rolling(self.params["period"]).std()
        self._upper = self._ma + self._std * self.params["std_dev"]
        self._lower = self._ma - self._std * self.params["std_dev"]
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["period"]:
            return None

        price = row["nav"]
        lower = self._lower.iloc[i]
        upper = self._upper.iloc[i]

        if price <= lower and not self._in_position:
            self._in_position = True
            return "buy"
        if price >= upper and self._in_position:
            self._in_position = False
            return "sell"
        return None

    def on_finish(self):
        self._in_position = False


class KDJStrategy(Strategy):
    """KDJ 策略
    K 上穿 D 且 K < 20 → 买入
    K 下穿 D 且 K > 80 → 卖出
    参数: period=9, k_smooth=3, d_smooth=3
    """
    name = "KDJ"
    params = {"period": 9, "k_smooth": 3, "d_smooth": 3}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        low = nav_data.get("low", close).rolling(self.params["period"]).min()
        high = nav_data.get("high", close).rolling(self.params["period"]).max()

        rsv = (close - low) / (high - low).replace(0, np.nan) * 100

        # K = SMA(RSV, k_smooth), D = SMA(K, d_smooth)
        k_smooth = self.params["k_smooth"]
        d_smooth = self.params["d_smooth"]
        self._k = rsv.ewm(span=k_smooth, adjust=False).mean()
        self._d = self._k.ewm(span=d_smooth, adjust=False).mean()
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["period"] + max(self.params["k_smooth"], self.params["d_smooth"]):
            return None

        k_prev = self._k.iloc[i - 1]
        d_prev = self._d.iloc[i - 1]
        k_cur = self._k.iloc[i]
        d_cur = self._d.iloc[i]

        # K 上穿 D 且低于 20
        if k_prev <= d_prev and k_cur > d_cur and k_cur < 20 and not self._in_position:
            self._in_position = True
            return "buy"
        # K 下穿 D 且高于 80
        if k_prev >= d_prev and k_cur < d_cur and k_cur > 80 and self._in_position:
            self._in_position = False
            return "sell"
        return None

    def on_finish(self):
        self._in_position = False


# ===================== 组合信号 =====================

class MARSIStrategy(Strategy):
    """双均线+RSI 组合
    价格在 MA 之上且 RSI 超卖 → 买入
    价格在 MA 之下且 RSI 超买 → 卖出
    参数: ma_period=50, rsi_period=14, oversold=30, overbought=70
    """
    name = "MA+RSI"
    params = {"ma_period": 50, "rsi_period": 14, "oversold": 30, "overbought": 70}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        self._ma = close.rolling(self.params["ma_period"]).mean()

        delta = close.diff()
        gain = delta.clip(lower=0)
        loss = (-delta).clip(lower=0)
        avg_gain = gain.rolling(self.params["rsi_period"], min_periods=self.params["rsi_period"]).mean()
        avg_loss = loss.rolling(self.params["rsi_period"], min_periods=self.params["rsi_period"]).mean()
        rs = avg_gain / avg_loss.replace(0, np.nan)
        self._rsi = 100 - (100 / (1 + rs))
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        min_bars = max(self.params["ma_period"], self.params["rsi_period"])
        if i < min_bars:
            return None

        price = row["nav"]
        ma_val = self._ma.iloc[i]
        rsi_val = self._rsi.iloc[i]

        if pd.isna(ma_val) or pd.isna(rsi_val):
            return None

        # 多头趋势 + 超卖 → 买入
        if price > ma_val and rsi_val < self.params["oversold"] and not self._in_position:
            self._in_position = True
            return "buy"
        # 空头趋势 + 超买 → 卖出
        if price < ma_val and rsi_val > self.params["overbought"] and self._in_position:
            self._in_position = False
            return "sell"
        return None

    def on_finish(self):
        self._in_position = False


class MACDBollingerStrategy(Strategy):
    """MACD+布林带 双确认
    MACD 金叉且价格靠近下轨 → 买入
    MACD 死叉且价格靠近上轨 → 卖出
    参数: fast=12, slow=26, signal=9, bb_period=20, bb_std=2, band_threshold=0.3
    """
    name = "MACD+布林带"
    params = {"fast": 12, "slow": 26, "signal": 9,
              "bb_period": 20, "bb_std": 2, "band_threshold": 0.3}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]

        # MACD
        ema_fast = close.ewm(span=self.params["fast"], adjust=False).mean()
        ema_slow = close.ewm(span=self.params["slow"], adjust=False).mean()
        self._dif = ema_fast - ema_slow
        self._dea = self._dif.ewm(span=self.params["signal"], adjust=False).mean()

        # 布林带
        bb_ma = close.rolling(self.params["bb_period"]).mean()
        bb_std = close.rolling(self.params["bb_period"]).std()
        self._bb_lower = bb_ma - bb_std * self.params["bb_std"]
        self._bb_upper = bb_ma + bb_std * self.params["bb_std"]
        self._bb_mid = bb_ma
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        min_bars = max(self.params["slow"] + self.params["signal"], self.params["bb_period"])
        if i < min_bars:
            return None

        price = row["nav"]
        lower = self._bb_lower.iloc[i]
        upper = self._bb_upper.iloc[i]
        mid = self._bb_mid.iloc[i]
        dif_prev = self._dif.iloc[i - 1]
        dea_prev = self._dea.iloc[i - 1]
        dif_cur = self._dif.iloc[i]
        dea_cur = self._dea.iloc[i]

        # 金叉 + 价格在下轨附近 (价格贴近下轨)
        if (dif_prev <= dea_prev and dif_cur > dea_cur and
                price <= lower * (1 + self.params["band_threshold"]) and
                not self._in_position):
            self._in_position = True
            return "buy"

        # 死叉 + 价格在上轨附近
        if (dif_prev >= dea_prev and dif_cur < dea_cur and
                price >= upper * (1 - self.params["band_threshold"]) and
                self._in_position):
            self._in_position = False
            return "sell"

        return None

    def on_finish(self):
        self._in_position = False


class TripleMAStrategy(Strategy):
    """三均线策略
    短期 > 中期 > 长期 → 买入（多头排列）
    短期 < 中期 < 长期 → 卖出（空头排列）
    参数: short=5, mid=10, long=20
    """
    name = "三均线"
    params = {"short": 5, "mid": 10, "long": 20}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        close = nav_data["nav"]
        self._ma_short = close.rolling(self.params["short"]).mean()
        self._ma_mid = close.rolling(self.params["mid"]).mean()
        self._ma_long = close.rolling(self.params["long"]).mean()
        self._prev_alignment = None  # 'bullish' | 'bearish' | None

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["long"]:
            return None

        s = self._ma_short.iloc[i]
        m = self._ma_mid.iloc[i]
        l = self._ma_long.iloc[i]

        if pd.isna(s) or pd.isna(m) or pd.isna(l):
            return None

        if s > m > l:
            if self._prev_alignment != "bullish":
                self._prev_alignment = "bullish"
                return "buy"
        elif s < m < l:
            if self._prev_alignment != "bearish":
                if self._prev_alignment == "bullish":
                    self._prev_alignment = "bearish"
                    return "sell"
                self._prev_alignment = "bearish"
        else:
            # 排列混乱，更新状态但不交易
            self._prev_alignment = "mixed"

        return None

    def on_finish(self):
        self._prev_alignment = None


# ===================== 仓位管理 =====================

class TrailingStopStrategy(Strategy):
    """移动止盈止损策略
    MA 上穿 → 买入，启动追踪
    价格从峰值回撤 trail_pct% → 卖出
    参数: ma_period=20, trail_pct=5
    """
    name = "移动止盈止损"
    params = {"ma_period": 20, "trail_pct": 8}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        self._ma = nav_data["nav"].rolling(self.params["ma_period"]).mean()
        self._peak_nav = 0.0
        self._in_position = False

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < self.params["ma_period"]:
            return None

        price = row["nav"]
        ma_val = self._ma.iloc[i]

        if not self._in_position:
            if price > ma_val:
                self._in_position = True
                self._peak_nav = price
                return "buy"
        else:
            # 更新峰值
            if price > self._peak_nav:
                self._peak_nav = price
            # 检查回撤
            drawdown = (self._peak_nav - price) / self._peak_nav * 100
            if drawdown >= self.params["trail_pct"]:
                self._in_position = False
                return "sell"

        return None

    def on_finish(self):
        self._in_position = False
        self._peak_nav = 0.0


class PyramidingStrategy(Strategy):
    """金字塔加仓策略
    MA 上穿 → 初始买入（全部资金）
    持仓后每下跌 add_pct% → 追加一层
    从峰值回撤 trail_pct% → 全部卖出
    参数: add_pct=5, max_layers=3, trail_pct=8
    """
    name = "金字塔加仓"
    params = {"add_pct": 5, "max_layers": 3, "trail_pct": 8}

    def on_init(self, nav_data: pd.DataFrame):
        super().on_init(nav_data)
        self._ma = nav_data["nav"].rolling(20).mean()
        self._layers = 0
        self._in_position = False
        self._peak_nav = 0.0
        self._last_buy_price = 0.0
        self._layer_base = nav_data["nav"].iloc[0] * 5000  # 每层约 3w（仅用于部分买入信号）

    def on_bar(self, row: pd.Series) -> Optional[str]:
        i = self._bar_index
        if i < 20:
            return None

        price = row["nav"]
        ma_val = self._ma.iloc[i]
        signal = None

        if not self._in_position:
            if price > ma_val:
                self._in_position = True
                self._layers = 1
                self._peak_nav = price
                self._last_buy_price = price
                signal = "buy"  # 全仓
        else:
            if price > self._peak_nav:
                self._peak_nav = price

            if (self._layers < self.params["max_layers"] and
                    self._last_buy_price > 0):
                drop = (self._last_buy_price - price) / self._last_buy_price * 100
                if drop >= self.params["add_pct"]:
                    self._layers += 1
                    self._last_buy_price = price
                    signal = ("buy", self._layer_base * self._layers)

            drawdown = (self._peak_nav - price) / self._peak_nav * 100
            if drawdown >= self.params["trail_pct"]:
                self._in_position = False
                self._layers = 0
                signal = "sell"

        return signal

    def on_finish(self):
        self._in_position = False
        self._layers = 0
        self._peak_nav = 0.0
