"""ETF/指数 K-line 数据抓取 + 导入

数据源: 腾讯行情 web.ifzq.gtimg.cn (免费，无需认证)
格式: [date, open, close, high, low, volume]

用法:
    python fetch_kline.py sh510050 --days=500
    python fetch_kline.py sz159915 --days=500
    python fetch_kline.py sh510050,sz159915,sh513100 --days=250
"""

import json
import re
import sys
import urllib.request
import urllib.error
import argparse
from db import get_connection

TENCENT_KLINE_API = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={code},day,,,{days},qfq"

# 常见 ETF/指数 代码
COMMON_ETF = {
    "sh510050": "上证50ETF",
    "sh510300": "沪深300ETF",
    "sh510500": "中证500ETF",
    "sh512100": "中证1000ETF",
    "sh513100": "纳指ETF",
    "sh513500": "标普500ETF",
    "sz159915": "创业板ETF",
    "sz159949": "创业板50ETF",
    "sz159845": "中证1000ETF(深圳)",
    "sz159766": "旅游ETF",
    "sz159865": "养殖ETF",
    "sh588000": "科创50ETF",
    "sh517050": "中证光伏ETF",
    "sz159790": "碳中和ETF",
    "sz159928": "消费ETF",
}

COMMON_INDEX = {
    "sh000001": "上证指数",
    "sz399001": "深证成指",
    "sz399006": "创业板指",
    "sh000688": "科创50",
    "sh000300": "沪深300",
    "sh000905": "中证500",
    "sh000852": "中证1000",
    "sh000016": "上证50",
}


def fetch_kline(code: str, days: int = 365) -> list[dict]:
    """从腾讯行情拉取 K-line 数据"""
    # 自动补全市场前缀
    code = _normalize_code(code)

    url = TENCENT_KLINE_API.format(code=code, days=days)
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0",
    })

    try:
        resp = urllib.request.urlopen(req, timeout=15)
        text = resp.read().decode("utf-8")
    except urllib.error.URLError as e:
        print(f"网络错误: {e}", file=sys.stderr)
        return []

    # 剥离 JSONP 回调
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        print(f"解析失败: {text[:100]}", file=sys.stderr)
        return []

    data = json.loads(m.group())
    stock_data = data.get("data", {}).get(code, {})
    # 尝试 qfqday (前复权日线) 或 day
    klines = stock_data.get("qfqday") or stock_data.get("day")
    if not klines:
        print(f"无 K-line 数据: {code}", file=sys.stderr)
        return []

    results = []
    for k in klines:
        if len(k) >= 6:
            results.append({
                "date": k[0],
                "open": float(k[1]),
                "close": float(k[2]),
                "high": float(k[3]),
                "low": float(k[4]),
                "volume": float(k[5]),
            })

    return results


def _normalize_code(code: str) -> str:
    """规范化代码格式: 510050 → sh510050, sh510050 → sh510050"""
    code = code.strip()
    if code.startswith("sh") or code.startswith("sz"):
        return code
    # 6 开头 → 上海, 0/3 开头 → 深圳
    if code.startswith("6") or code.startswith("9"):
        return f"sh{code}"
    return f"sz{code}"


def import_kline(code: str, records: list[dict]) -> int:
    """导入 K-line 到数据库"""
    conn = get_connection()
    count = 0
    for r in records:
        try:
            conn.execute(
                """INSERT OR IGNORE INTO kline (code, date, open, close, high, low, volume)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (code, r["date"], r["open"], r["close"], r["high"], r["low"], r["volume"]),
            )
            count += 1
        except Exception as e:
            print(f"导入失败 [{code} {r.get('date')}]: {e}", file=sys.stderr)
    conn.commit()
    conn.close()
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ETF/指数 K-line 数据抓取")
    parser.add_argument("codes", help="代码（逗号分隔，如 sh510050,sz159915）")
    parser.add_argument("--days", type=int, default=365, help="拉取天数")
    args = parser.parse_args()

    codes = [c.strip() for c in args.codes.split(",") if c.strip()]
    total = 0
    for code in codes:
        records = fetch_kline(code, args.days)
        print(f"{code}: 拉取到 {len(records)} 条 K-line")
        if records:
            count = import_kline(code, records)
            total += count
            print(f"  → 导入 {count} 条")

    from db import get_kline_count
    print(f"\nK-line 共 {get_kline_count()} 条记录")
