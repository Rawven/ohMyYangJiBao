"""从 fund-list.json 导入基金列表到数据库"""

import json
import os
import sys
from db import get_connection

DATA_DIR = os.path.expanduser("~/.ohmyyangjibao")
FUND_LIST_PATH = os.path.join(DATA_DIR, "fund-list.json")


def import_funds(json_path: str = FUND_LIST_PATH) -> int:
    """导入基金列表，返回导入数量"""
    if not os.path.exists(json_path):
        print(f"文件不存在: {json_path}", file=sys.stderr)
        print("请先用 node tools/get-fund-list.mjs 抓取基金列表", file=sys.stderr)
        return 0

    with open(json_path, "r", encoding="utf-8") as f:
        fund_list = json.load(f)

    conn = get_connection()
    count = 0
    for fund in fund_list:
        try:
            conn.execute(
                """INSERT OR IGNORE INTO funds (code, name, type)
                   VALUES (?, ?, ?)""",
                (fund["code"], fund["name"], fund.get("type")),
            )
            count += 1
        except Exception as e:
            print(f"导入失败 [{fund.get('code')}]: {e}", file=sys.stderr)
    conn.commit()
    conn.close()
    return count


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else FUND_LIST_PATH
    count = import_funds(path)
    print(f"导入完成: {count} 条基金记录")

    from db import get_fund_count
    print(f"数据库当前共 {get_fund_count()} 只基金")
