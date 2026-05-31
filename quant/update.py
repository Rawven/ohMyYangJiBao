"""数据更新管道：调用 Node.js 抓取最新数据 → 导入数据库

支持增量更新，只拉取最新净值，不重复导入已有数据。
"""

import os
import subprocess
import sys
from datetime import datetime, timedelta
from import_nav import fetch_nav_from_api, import_nav
from db import get_connection, get_last_nav_date


TOOLS_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "tools")


def update_fund_list() -> int:
    """调用 Node.js 更新基金列表"""
    print("更新基金列表...")
    result = subprocess.run(
        ["node", os.path.join(TOOLS_DIR, "get-fund-list.mjs")],
        capture_output=True, text=True, timeout=120,
    )
    if result.returncode != 0:
        print(f"抓取基金列表失败: {result.stderr}", file=sys.stderr)
        return 0

    # 导入到数据库
    from import_funds import import_funds
    count = import_funds()
    print(f"基金列表更新完成: {count} 只")
    return count


def update_nav(code: str, force_days: int | None = None) -> int:
    """增量更新某只基金的净值

    从数据库获取最新日期，只拉取之后的数据。
    如果没有历史数据，拉取 365 天。
    """
    last_date = get_last_nav_date(code)

    if last_date and force_days is None:
        # 计算需要拉取的天数（从最新日期的次日到今日）
        last_dt = datetime.strptime(last_date, "%Y-%m-%d")
        days = (datetime.now() - last_dt).days + 5  # 多拉 5 天做缓冲
        days = max(10, min(days, 365))
    else:
        days = force_days or 365

    print(f"  拉取 {code} 最近 {days} 天净值...")
    records = fetch_nav_from_api(code, days)

    # 过滤掉已有数据
    if last_date:
        records = [r for r in records if r["date"] > last_date]

    if not records:
        print(f"  {code}: 无需更新")
        return 0

    count = import_nav(code, records)
    print(f"  {code}: 新增 {count} 条净值 (从 {records[0]['date']} 到 {records[-1]['date']})")
    return count


def update_all_nav(days: int = 30) -> int:
    """更新所有已有净值记录的基金"""
    conn = get_connection()
    rows = conn.execute(
        "SELECT DISTINCT code FROM nav_history ORDER BY code"
    ).fetchall()
    conn.close()

    total = 0
    for i, row in enumerate(rows):
        code = row["code"]
        try:
            total += update_nav(code, days)
        except Exception as e:
            print(f"  {code}: 更新失败 - {e}", file=sys.stderr)

        if (i + 1) % 20 == 0:
            print(f"  进度: {i + 1}/{len(rows)}")

    return total


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="数据更新管道")
    parser.add_argument("--code", help="基金代码（不指定则更新所有）")
    parser.add_argument("--days", type=int, default=30, help="拉取天数")
    parser.add_argument("--fund-list", action="store_true", help="同时更新基金列表")
    args = parser.parse_args()

    if args.fund_list:
        update_fund_list()

    if args.code:
        count = update_nav(args.code, args.days)
        print(f"更新完成: {count} 条")
    else:
        total = update_all_nav(args.days)
        print(f"全部更新完成: 共 {total} 条")
