"""从 API 或 Node.js 输出导入净值数据到数据库

用法:
    # 从 API 直接拉取
    python import_nav.py --code=001856 --days=365

    # 从 Node.js 输出导入 (管道)
    node tools/get-nav-history.mjs --code=001856 | python import_nav.py --code=001856
"""

import json
import sys
import urllib.request
import re
import argparse
from db import get_connection


def fetch_nav_from_api(code: str, days: int = 365) -> list[dict]:
    """从东方财富 API 直接获取净值"""
    all_data = []
    page_size = 20
    total_pages = (days + page_size - 1) // page_size

    for page in range(1, total_pages + 1):
        url = (
            f"https://api.fund.eastmoney.com/f10/lsjz"
            f"?fundCode={code}&pageIndex={page}&pageSize=50"
        )
        req = urllib.request.Request(
            url,
            headers={
                "Referer": f"https://fundf10.eastmoney.com/jbgk_{code}.html",
                "User-Agent": "Mozilla/5.0",
            },
        )
        resp = urllib.request.urlopen(req, timeout=15)
        text = resp.read().decode("utf-8")

        # strip JSONP
        m = re.search(r"\{.*\}", text, re.DOTALL)
        if not m:
            break
        data = json.loads(m.group())
        lsjz_list = data.get("Data", {}).get("LSJZList", [])
        if not lsjz_list:
            break

        for item in lsjz_list:
            all_data.append({
                "date": item["FSRQ"],
                "nav": float(item["DWJZ"]),
                "acc_nav": float(item["LJJZ"]) if item.get("LJJZ") else None,
            })

        if len(lsjz_list) < page_size:
            break

    all_data.sort(key=lambda x: x["date"])
    return all_data


def import_nav(code: str, records: list[dict]) -> int:
    """导入净值记录，返回导入数量"""
    conn = get_connection()
    count = 0
    for r in records:
        try:
            conn.execute(
                """INSERT OR IGNORE INTO nav_history (code, date, nav, acc_nav)
                   VALUES (?, ?, ?, ?)""",
                (code, r["date"], r["nav"], r.get("acc_nav")),
            )
            count += 1
        except Exception as e:
            print(f"导入失败 [{code} {r.get('date')}]: {e}", file=sys.stderr)
    conn.commit()
    conn.close()
    return count


def read_json_from_stdin() -> list[dict]:
    """从 stdin 读取 JSON"""
    text = sys.stdin.read().strip()
    if not text:
        return []
    return json.loads(text)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="导入净值数据")
    parser.add_argument("--code", required=True, help="基金代码")
    parser.add_argument("--days", type=int, default=365, help="拉取天数")
    parser.add_argument("--from-stdin", action="store_true", help="从 stdin 读取 JSON")
    args = parser.parse_args()

    if args.from_stdin:
        records = read_json_from_stdin()
        print(f"从 stdin 读取到 {len(records)} 条记录")
    else:
        records = fetch_nav_from_api(args.code, args.days)
        print(f"从 API 拉取到 {len(records)} 条记录")

    if records:
        count = import_nav(args.code, records)
        print(f"导入完成: {count} 条净值记录")
    else:
        print("无数据可导入")

    from db import get_nav_count
    print(f"{args.code} 当前共 {get_nav_count(args.code)} 条净值记录")
