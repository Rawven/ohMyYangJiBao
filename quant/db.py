"""数据库初始化与连接管理"""

import os
import sqlite3

DATA_DIR = os.path.expanduser("~/.ohmyyangjibao")
DB_PATH = os.path.join(DATA_DIR, "quant.db")
SCHEMA_PATH = os.path.join(os.path.dirname(__file__), "schema.sql")


def get_connection() -> sqlite3.Connection:
    """获取数据库连接"""
    os.makedirs(DATA_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def init_db():
    """初始化数据库，建表"""
    conn = get_connection()
    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        conn.executescript(f.read())
    conn.commit()
    conn.close()


def get_fund_count() -> int:
    """获取基金总数"""
    conn = get_connection()
    row = conn.execute("SELECT COUNT(*) AS cnt FROM funds").fetchone()
    conn.close()
    return row["cnt"]


def get_nav_count(code: str = None) -> int:
    """获取净值记录数"""
    conn = get_connection()
    if code:
        row = conn.execute(
            "SELECT COUNT(*) AS cnt FROM nav_history WHERE code = ?", (code,)
        ).fetchone()
    else:
        row = conn.execute("SELECT COUNT(*) AS cnt FROM nav_history").fetchone()
    conn.close()
    return row["cnt"]


def get_last_nav_date(code: str) -> str | None:
    """获取某基金最新净值日期"""
    conn = get_connection()
    row = conn.execute(
        "SELECT MAX(date) AS max_date FROM nav_history WHERE code = ?", (code,)
    ).fetchone()
    conn.close()
    return row["max_date"] if row and row["max_date"] else None


def get_kline_count(code: str = None) -> int:
    """获取 K-line 记录数"""
    conn = get_connection()
    if code:
        row = conn.execute(
            "SELECT COUNT(*) AS cnt FROM kline WHERE code = ?", (code,)
        ).fetchone()
    else:
        row = conn.execute("SELECT COUNT(*) AS cnt FROM kline").fetchone()
    conn.close()
    return row["cnt"]


def get_last_kline_date(code: str) -> str | None:
    """获取某 ETF 最新 K-line 日期"""
    conn = get_connection()
    row = conn.execute(
        "SELECT MAX(date) AS max_date FROM kline WHERE code = ?", (code,)
    ).fetchone()
    conn.close()
    return row["max_date"] if row and row["max_date"] else None


if __name__ == "__main__":
    init_db()
    print(f"数据库初始化完成: {DB_PATH}")
