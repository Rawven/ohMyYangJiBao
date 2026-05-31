"""命令行入口

用法:
    # 初始化数据库
    python cli.py init

    # 数据导入
    python cli.py import funds
    python cli.py import nav --code=001856 [--days=365]

    # 回测
    python cli.py run 买入持有 --code=001856
    python cli.py run 均线交叉 --code=001856 --short=5 --long=20
    python cli.py run 定投 --code=001856 --amount=1000 --day=1
    python cli.py run 动量 --code=001856 --lookback=20
    python cli.py run 估值百分位 --code=001856 --pe-low=20 --pe-high=80
    python cli.py run 网格 --code=001856 --grid-low=5 --grid-high=10 --grid-count=10

    # 数据更新
    python cli.py update --code=001856 [--days=30]

    # 查询
    python cli.py list [--limit=20]
    python cli.py results [--limit=10]
"""

import argparse
import importlib.util
import json
import os
import sys
import subprocess

# 确保能找到 quant 包内的模块
sys.path.insert(0, os.path.dirname(__file__))

from db import init_db, get_fund_count, get_nav_count
from strategies import STRATEGIES, get_strategy
from backtest import BacktestRunner
from report import print_report, list_results
from import_funds import import_funds


def cmd_init(args):
    """初始化数据库"""
    init_db()
    print(f"数据库初始化完成")
    print(f"  基金数量: {get_fund_count()}")
    print(f"  数据目录: ~/.ohmyyangjibao/quant.db")


def cmd_import(args):
    """导入数据"""
    if args.type == "funds":
        count = import_funds()
        print(f"导入完成: {count} 只基金")
        print(f"数据库共 {get_fund_count()} 只基金")

    elif args.type == "nav":
        from import_nav import fetch_nav_from_api, import_nav

        days = args.days or 365
        records = fetch_nav_from_api(args.code, days)
        print(f"从 API 拉取到 {len(records)} 条净值记录")
        if records:
            count = import_nav(args.code, records)
            print(f"导入完成: {count} 条")
            print(f"  {args.code} 当前共 {get_nav_count(args.code)} 条")

    elif args.type == "kline":
        from fetch_kline import fetch_kline, import_kline

        codes = [c.strip() for c in args.code.split(",") if c.strip()]
        total = 0
        for code in codes:
            records = fetch_kline(code, args.days or 365)
            print(f"  {code}: 拉取到 {len(records)} 条")
            if records:
                cnt = import_kline(code, records)
                total += cnt
                print(f"  → 导入 {cnt} 条")
        from db import get_kline_count
        print(f"K-line 共 {get_kline_count()} 条记录")
    else:
        print(f"未知导入类型: {args.type}，支持: funds, nav, kline")


def cmd_run(args):
    """运行回测"""
    # 构建策略参数: --param key=value 方式
    params = {}
    if args.param:
        for p in args.param:
            if "=" in p:
                k, v = p.split("=", 1)
                # 自动类型转换
                try:
                    if "." in v:
                        v = float(v)
                    else:
                        v = int(v)
                except ValueError:
                    pass
                params[k] = v

    # 兼容旧的单独参数形式
    compatibility_map = {
        "short": ("short_win", int), "long": ("long_win", int),
        "amount": ("amount", float), "day": ("day_of_month", int),
        "lookback": ("lookback_days", int),
        "pe_low": ("pe_low", float), "pe_high": ("pe_high", float),
        "grid_low": ("grid_low", float), "grid_high": ("grid_high", float),
        "grid_count": ("grid_count", int),
    }
    for arg_key, (param_key, cast) in compatibility_map.items():
        val = getattr(args, arg_key, None)
        if val is not None and param_key not in params:
            params[param_key] = cast(val)

    strategy = get_strategy(args.strategy_name, **params)
    runner = BacktestRunner(strategy)
    data_source = getattr(args, "kline", False) and "kline" or "nav"
    result = runner.run(args.code, args.start, args.end, data_source=data_source)

    # 保存结果
    from db import get_connection
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

    # 输出报告
    print_report(result, show_chart=not args.no_chart)


def cmd_update(args):
    """更新数据：调用 Node.js 脚本抓取最新数据并导入"""
    from import_nav import fetch_nav_from_api, import_nav

    days = args.days or 30
    codes = [args.code] if args.code else []

    if not codes:
        # 默认更新所有有净值记录的基金
        from db import get_connection
        conn = get_connection()
        rows = conn.execute(
            "SELECT DISTINCT code FROM nav_history"
        ).fetchall()
        conn.close()
        codes = [r["code"] for r in rows]

    total = 0
    for code in codes:
        try:
            records = fetch_nav_from_api(code, days)
            if records:
                count = import_nav(code, records)
                total += count
                print(f"  {code}: 更新 {count} 条")
        except Exception as e:
            print(f"  {code}: 更新失败 - {e}", file=sys.stderr)

    print(f"更新完成，共 {total} 条记录")


def cmd_list(args):
    """列出已导入的基金"""
    from db import get_connection

    limit = args.limit or 30
    keyword = args.keyword

    conn = get_connection()
    if keyword:
        rows = conn.execute(
            """SELECT code, name, type, company,
                      (SELECT COUNT(*) FROM nav_history WHERE code = funds.code) AS nav_count
               FROM funds
               WHERE name LIKE ? OR code LIKE ?
               ORDER BY code
               LIMIT ?""",
            (f"%{keyword}%", f"%{keyword}%", limit),
        ).fetchall()
    else:
        rows = conn.execute(
            """SELECT code, name, type, company,
                      (SELECT COUNT(*) FROM nav_history WHERE code = funds.code) AS nav_count
               FROM funds
               ORDER BY code
               LIMIT ?""",
            (limit,),
        ).fetchall()
    conn.close()

    if not rows:
        print("数据库为空，请先执行 python cli.py import funds")
        return

    sep = "=" * 72
    print(sep)
    print(f"  {'代码':<8} {'名称':<28} {'类型':<14} {'净值记录':>8}")
    print(f"  {'─' * 8} {'─' * 28} {'─' * 14} {'─' * 8}")
    for r in rows:
        name = r["name"][:14] + "…" if len(r["name"]) > 14 else r["name"]
        print(f"  {r['code']:<8} {name:<28} {(r['type'] or ''):<14} {r['nav_count']:>8}")
    print(sep)
    print(f"  共 {len(rows)} 条")


def cmd_rotate(args):
    """运行动量轮动"""
    codes = [c.strip() for c in args.codes.split(",") if c.strip()]
    if len(codes) < 2:
        print("轮动至少需要 2 只标的")
        return

    from rotator import RotationRunner, print_rotation_report

    data_source = getattr(args, "kline", False) and "kline" or "nav"
    runner = RotationRunner(
        lookback=args.lookback,
        top_k=args.top_k,
        rebalance_days=args.rebalance,
        data_source=data_source,
    )
    result = runner.run(codes, args.start, args.end)
    print_rotation_report(result, show_chart=not args.no_chart)


def main():
    parser = argparse.ArgumentParser(
        description="OhMyYangJiBao 量化系统",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python cli.py init                          初始化数据库
  python cli.py import funds                  导入基金列表
  python cli.py import nav --code=001856      导入净值
  python cli.py run 买入持有 --code=001856    买入持有回测
  python cli.py run 均线交叉 --code=001856 --short=5 --long=20
  python cli.py run 定投 --code=001856 --amount=1000 --day=1
  python cli.py update --code=001856          更新净值
  python cli.py list --limit=20               列出基金
  python cli.py results                       查看回测记录
        """,
    )
    subparsers = parser.add_subparsers(dest="command")

    # init
    parser_init = subparsers.add_parser("init", help="初始化数据库")

    # import
    parser_import = subparsers.add_parser("import", help="导入数据")
    parser_import.add_argument("type", choices=["funds", "nav", "kline"], help="导入类型")
    parser_import.add_argument("--code", help="基金/ETF 代码（kline 支持逗号分隔）")
    parser_import.add_argument("--days", type=int, default=250, help="拉取天数")

    # run
    parser_run = subparsers.add_parser("run", help="运行回测")
    parser_run.add_argument("strategy_name", help=f"策略名称: {'/'.join(STRATEGIES.keys())}")
    parser_run.add_argument("--code", required=True, help="基金代码")
    parser_run.add_argument("--start", default=None, help="开始日期 YYYY-MM-DD")
    parser_run.add_argument("--end", default=None, help="结束日期 YYYY-MM-DD")
    parser_run.add_argument("--param", action="append", help="策略参数 key=value（可多次使用）")
    parser_run.add_argument("--kline", action="store_true", help="使用 ETF K-line 数据")
    parser_run.add_argument("--no-chart", action="store_true", help="不生成图表")
    # 兼容旧参数
    parser_run.add_argument("--short", type=int, help=argparse.SUPPRESS)
    parser_run.add_argument("--long", type=int, help=argparse.SUPPRESS)
    parser_run.add_argument("--amount", type=float, help=argparse.SUPPRESS)
    parser_run.add_argument("--day", type=int, help=argparse.SUPPRESS)
    parser_run.add_argument("--lookback", type=int, help=argparse.SUPPRESS)
    parser_run.add_argument("--pe-low", type=float, dest="pe_low", help=argparse.SUPPRESS)
    parser_run.add_argument("--pe-high", type=float, dest="pe_high", help=argparse.SUPPRESS)
    parser_run.add_argument("--grid-low", type=float, dest="grid_low", help=argparse.SUPPRESS)
    parser_run.add_argument("--grid-high", type=float, dest="grid_high", help=argparse.SUPPRESS)
    parser_run.add_argument("--grid-count", type=int, dest="grid_count", help=argparse.SUPPRESS)

    # rotate
    parser_rotate = subparsers.add_parser("rotate", help="动量轮动（多标的轮动）")
    parser_rotate.add_argument("--codes", required=True, help="代码，逗号分隔")
    parser_rotate.add_argument("--start", default=None, help="开始日期 YYYY-MM-DD")
    parser_rotate.add_argument("--end", default=None, help="结束日期 YYYY-MM-DD")
    parser_rotate.add_argument("--lookback", type=int, default=20, help="动量回看天数")
    parser_rotate.add_argument("--top-k", type=int, default=3, help="持有涨幅前 K 只")
    parser_rotate.add_argument("--rebalance", type=int, default=20, help="调仓间隔天数")
    parser_rotate.add_argument("--kline", action="store_true", help="使用 K-line 数据")
    parser_rotate.add_argument("--no-chart", action="store_true", help="不生成图表")

    # update
    parser_update = subparsers.add_parser("update", help="更新最新净值")
    parser_update.add_argument("--code", help="基金代码（不指定则更新所有）")
    parser_update.add_argument("--days", type=int, default=30, help="拉取天数")

    # list
    parser_list = subparsers.add_parser("list", help="列出基金")
    parser_list.add_argument("--limit", type=int, default=30, help="数量")
    parser_list.add_argument("--keyword", help="搜索关键词")

    # results
    parser_results = subparsers.add_parser("results", help="查看回测结果")
    parser_results.add_argument("--limit", type=int, default=10, help="数量")

    args = parser.parse_args()

    if args.command == "init":
        cmd_init(args)
    elif args.command == "import":
        cmd_import(args)
    elif args.command == "run":
        cmd_run(args)
    elif args.command == "update":
        cmd_update(args)
    elif args.command == "list":
        cmd_list(args)
    elif args.command == "results":
        list_results(args.limit)
    elif args.command == "rotate":
        cmd_rotate(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
