"""回测报告生成：终端表格 + matplotlib 图表"""

from __future__ import annotations

import os
from typing import Optional

from backtest import BacktestResult

try:
    import matplotlib
    import matplotlib.pyplot as plt
    import matplotlib.dates as mdates
    from matplotlib.ticker import FuncFormatter

    HAS_MPL = True
except ImportError:
    HAS_MPL = False

DATA_DIR = os.path.expanduser("~/.ohmyyangjibao")
CHART_DIR = os.path.join(DATA_DIR, "charts")


def _ensure_chart_dir():
    os.makedirs(CHART_DIR, exist_ok=True)


def print_report(result: BacktestResult, show_chart: bool = True):
    """终端输出回测报告"""
    sep = "=" * 56
    print(sep)
    print(f"  回测报告: {result.strategy}")
    print(f"  基金代码: {result.fund_code}")
    print(sep)

    # 基本信息
    print(f"  回测区间:  {result.start_date} → {result.end_date}")
    print(f"  策略参数:  {result.params}")
    print()

    # 绩效指标
    print(f"  {'指标':<16} {'数值':>10}")
    print(f"  {'─' * 16} {'─' * 10}")
    _print_row("总收益率", f"{result.total_return:+.2f}%")
    _print_row("年化收益率", f"{result.annual_return:+.2f}%")
    _print_row("最大回撤", f"{result.max_drawdown:.2f}%")
    _print_row("夏普比率", f"{result.sharpe:.2f}")
    _print_row("胜率", f"{result.win_rate:.1f}%")
    _print_row("交易次数", str(result.trade_count))
    print()

    # 交易记录
    if result.trades:
        print(f"  交易记录 ({len(result.trades)} 笔):")
        print(f"  {'日期':<12} {'方向':<6} {'净值':>8} {'金额':>10} {'费用':>8}")
        print(f"  {'─' * 12} {'─' * 6} {'─' * 8} {'─' * 10} {'─' * 8}")
        for t in result.trades:
            direction = "买入" if t.direction == "buy" else "卖出"
            print(
                f"  {t.date:<12} {direction:<6} {t.price:>8.4f} "
                f"{t.amount:>10.2f} {t.fee:>8.2f}"
            )
        print()

    # 净值曲线尾部
    if result.equity_curve:
        last = result.equity_curve[-1]
        print(f"  最新: 净值={last.nav:.4f}  市值={last.total_value:,.2f}")
        print()

    print(sep)

    # 图表
    if show_chart and HAS_MPL:
        _plot_chart(result)


def _print_row(label: str, value: str):
    print(f"  {label:<16} {value:>10}")


def _plot_chart(result: BacktestResult):
    """绘制回测图表并保存"""
    _ensure_chart_dir()
    matplotlib.rcParams["font.sans-serif"] = ["PingFang SC", "Heiti TC", "WenQuanYi Micro Hei", "SimHei"]
    matplotlib.rcParams["axes.unicode_minus"] = False

    curve = result.equity_curve
    dates = [c.date for c in curve]
    navs = [c.nav for c in curve]
    values = [c.total_value for c in curve]

    # 计算基准（买入持有）的净值曲线
    initial_value = values[0] if values else 1
    buy_hold_values = [v / values[0] * initial_value for v in values] if values else []

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8), sharex=True)

    x = range(len(dates))
    # x 轴标签：只显示部分日期
    tick_step = max(1, len(dates) // 8)

    # 上：净值曲线
    ax1.plot(x, navs, label="净值", color="#2196F3", linewidth=1)
    ax1.set_ylabel("单位净值")
    ax1.legend(loc="upper left")
    ax1.grid(True, alpha=0.3)
    ax1.set_title(f"{result.strategy} — {result.fund_code}")

    # 标记买卖点
    buy_dates = {t.date for t in result.trades if t.direction == "buy"}
    sell_dates = {t.date for t in result.trades if t.direction == "sell"}
    for i, pt in enumerate(curve):
        if pt.date in buy_dates:
            ax1.scatter(i, pt.nav, color="#f44336", marker="^", s=80, zorder=5)
        if pt.date in sell_dates:
            ax1.scatter(i, pt.nav, color="#4CAF50", marker="v", s=80, zorder=5)

    # 下：市值曲线 vs 简单买入持有
    ax2.plot(x, values, label="策略市值", color="#FF5722", linewidth=1.5)
    if len(buy_hold_values) == len(values):
        ax2.plot(x, buy_hold_values, label="买入持有", color="#9E9E9E", linewidth=1, linestyle="--")
    ax2.set_ylabel("市值")
    ax2.legend(loc="upper left")
    ax2.grid(True, alpha=0.3)

    # x 轴标签
    tick_positions = list(range(0, len(dates), tick_step))
    tick_labels = [dates[i] for i in tick_positions]
    ax2.set_xticks(tick_positions)
    ax2.set_xticklabels(tick_labels, rotation=30, ha="right")
    ax2.set_xlabel("日期")

    plt.tight_layout()

    # 保存
    filename = f"{result.fund_code}_{result.strategy}.png"
    filepath = os.path.join(CHART_DIR, filename)
    plt.savefig(filepath, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  图表已保存: {filepath}")


def list_results(limit: int = 10):
    """列出最近的回测结果"""
    from db import get_connection

    conn = get_connection()
    rows = conn.execute(
        """SELECT id, strategy, fund_code, total_return, annual_return,
                  max_drawdown, sharpe, created_at
           FROM backtest_results
           ORDER BY created_at DESC
           LIMIT ?""",
        (limit,),
    ).fetchall()
    conn.close()

    if not rows:
        print("暂无回测记录")
        return

    sep = "=" * 72
    print(sep)
    print(f"  {'ID':<4} {'策略':<12} {'基金':<8} {'总收益':>8} {'年化':>8} {'回撤':>8} {'夏普':>6} {'时间'}")
    print(f"  {'─' * 4} {'─' * 12} {'─' * 8} {'─' * 8} {'─' * 8} {'─' * 8} {'─' * 6} {'─' * 10}")
    for r in rows:
        print(
            f"  {r['id']:<4} {r['strategy']:<12} {r['fund_code']:<8} "
            f"{r['total_return']:>+7.2f}% {r['annual_return']:>+7.2f}% "
            f"{r['max_drawdown']:>7.2f}% {r['sharpe']:>6.2f}  "
            f"{r['created_at'][:10]}"
        )
    print(sep)
