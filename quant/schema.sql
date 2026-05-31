-- 量化系统数据库 DDL
-- 数据存储目录: ~/.ohmyyangjibao/quant.db

CREATE TABLE IF NOT EXISTS funds (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    type        TEXT,
    company     TEXT,
    establish_date TEXT,
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nav_history (
    code        TEXT NOT NULL,
    date        TEXT NOT NULL,
    nav         REAL NOT NULL,
    acc_nav     REAL,
    updated_at  TEXT DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, date),
    FOREIGN KEY (code) REFERENCES funds(code)
);

CREATE TABLE IF NOT EXISTS indices (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    price       REAL,
    pe          REAL,
    pb          REAL,
    pe_percentile REAL,
    updated_at  TEXT
);

CREATE TABLE IF NOT EXISTS backtest_results (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    strategy    TEXT NOT NULL,
    fund_code   TEXT NOT NULL,
    params      TEXT,
    start_date  TEXT,
    end_date    TEXT,
    total_return REAL,
    annual_return REAL,
    max_drawdown REAL,
    sharpe       REAL,
    win_rate    REAL,
    trade_count INTEGER,
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nav_code_date ON nav_history(code, date);
CREATE INDEX IF NOT EXISTS idx_nav_date ON nav_history(date);

CREATE TABLE IF NOT EXISTS kline (
    code        TEXT NOT NULL,
    date        TEXT NOT NULL,
    open        REAL NOT NULL,
    close       REAL NOT NULL,
    high        REAL NOT NULL,
    low         REAL NOT NULL,
    volume      REAL,
    updated_at  TEXT DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, date)
);

CREATE INDEX IF NOT EXISTS idx_kline_code_date ON kline(code, date);

CREATE TABLE IF NOT EXISTS etf_list (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    market      TEXT NOT NULL DEFAULT 'sh',
    type        TEXT
);
