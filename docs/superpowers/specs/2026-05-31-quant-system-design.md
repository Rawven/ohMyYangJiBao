# 量化系统设计文档（第一阶段）

## 概述

基于现有 OhMyYangJiBao 基金数据爬虫（Node.js），构建 Python 量化系统。第一阶段目标：数据仓库 + 回测引擎。

现有工具提供数据源（天天基金/东方财富/腾讯行情），新系统负责数据存储、策略回测、绩效分析。

## 技术栈

- Python 3.10+（标准库 + sqlite3）
- pandas（数据处理）
- numpy（数值计算）
- matplotlib（图表输出）
- 零外部依赖可用，pandas/numpy/matplotlib 为可选增强

## 项目结构

```
quant/
├── db.py              # SQLite 初始化、连接、建表
├── schema.sql         # 数据库 DDL（独立文件方便审查）
├── import_funds.py    # 从 fund-list.json 导入基金列表
├── import_nav.py      # 从 Node.js get-nav-history 输出导入净值
├── update.py          # 调用 Node.js 脚本更新数据
├── backtest.py        # 回测引擎：策略基类、运行器、绩效计算
├── strategies.py      # 内置策略（均线交叉、估值百分位、定投等）
├── report.py          # 回测报告生成（终端表格 + 图表）
├── cli.py             # 命令行入口
└── requirements.txt   # pandas numpy matplotlib（可选）
```

## 数据模型（SQLite）

### funds 表
```sql
CREATE TABLE funds (
    code        TEXT PRIMARY KEY,         -- 基金代码
    name        TEXT NOT NULL,            -- 基金名称
    type        TEXT,                     -- 基金类型
    company     TEXT,                     -- 基金公司
    establish_date TEXT,                  -- 成立日期
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP
);
```

### nav_history 表
```sql
CREATE TABLE nav_history (
    code        TEXT NOT NULL,            -- 基金代码
    date        TEXT NOT NULL,            -- 日期 YYYY-MM-DD
    nav         REAL NOT NULL,            -- 单位净值
    acc_nav     REAL,                     -- 累计净值
    updated_at  TEXT DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code, date),
    FOREIGN KEY (code) REFERENCES funds(code)
);
```

### indices 表（指数估值）
```sql
CREATE TABLE indices (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    price       REAL,
    pe          REAL,
    pb          REAL,
    pe_percentile REAL,
    updated_at  TEXT
);
```

### backtest_results 表
```sql
CREATE TABLE backtest_results (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    strategy    TEXT NOT NULL,            -- 策略名称
    fund_code   TEXT NOT NULL,            -- 标的基金
    params      TEXT,                     -- JSON 策略参数
    start_date  TEXT,
    end_date    TEXT,
    total_return REAL,                    -- 总收益率 %
    annual_return REAL,                   -- 年化收益率 %
    max_drawdown REAL,                    -- 最大回撤 %
    sharpe       REAL,                    -- 夏普比率
    win_rate    REAL,                     -- 胜率 %
    trade_count INTEGER,                  -- 交易次数
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP
);
```

## 数据管道

### 数据导入流程

```
Node.js 脚本 (现有)              Python 导入 (新增)
─────────────────              ─────────────────
node get-fund-list       →      python import_funds.py
  → fund-list.json                → INSERT INTO funds

node get-nav-history     →      python import_nav.py
  → nav JSON                     → INSERT INTO nav_history

node get-index-valuation →      (直接存 indices 表)
```

### 数据更新策略

- `update.py` 封装：调用 Node.js 脚本抓取最新数据，调用 Python 导入
- 支持增量更新（只拉取最新净值，不重复导入已有数据）
- 首次全量导入后，每日增量更新

## 回测引擎设计

### 策略接口

```python
class Strategy:
    name: str
    params: dict
    
    def on_init(self):           # 策略初始化
    def on_bar(self, row):       # 每根 K 线/每个交易日调用
        # 返回信号: 'buy' / 'sell' / None
    def on_finish(self):         # 回测结束
```

### 运行器流程

1. 从 `nav_history` 加载时间段数据
2. 逐行传递给 `on_bar`（模拟时间推进）
3. 收到 buy/sell 信号时执行交易（记录持仓变化）
4. 回测结束后调用 `on_finish`
5. 计算绩效指标

### 交易模拟规则

- 买入：以当日净值全额买入，扣除申购费（可配）
- 卖出：以当日净值全部卖出，扣除赎回费（可配）
- 支持分批买入（定投模式）
- 不支持做空、杠杆

### 内置策略（第一期）

| 策略 | 说明 | 参数 |
|------|------|------|
| 均线交叉 | 短期均线上穿长期均线买入，下穿卖出 | short_win, long_win |
| 估值百分位 | PE 百分位低于阈值买入，高于阈值卖出 | pe_low, pe_high |
| 定投 | 每月固定日期固定金额买入 | amount, day_of_month |
| 网格 | 设定价格区间，等间距分批买入/卖出 | grid_low, grid_high, grid_count |
| 动量 | 过去N日涨幅排名靠前的买入 | lookback_days, top_n |

## 绩效指标

| 指标 | 计算方式 |
|------|----------|
| 总收益率 | (最终市值 - 总投入) / 总投入 × 100% |
| 年化收益率 | (1 + 总收益率)^(250/交易日数) - 1 |
| 最大回撤 | 峰值到谷值的最大跌幅 |
| 夏普比率 | (年化收益 - 无风险利率) / 年化波动率 |
| 胜率 | 盈利交易次数 / 总交易次数 |
| 交易次数 | 买入+卖出总次数 |

## CLI 接口

```bash
# 数据管理
python cli.py import funds          # 导入基金列表
python cli.py import nav --code=001856  # 导入净值
python cli.py update --code=001856  # 更新最新净值

# 回测
python cli.py run 均线交叉 --code=001856 --short=5 --long=20
python cli.py run 定投 --code=001856 --amount=1000
python cli.py run 估值百分位 --code=001856 --pe-low=30

# 查看
python cli.py list          # 查看已导入的基金
python cli.py results       # 查看历史回测结果
```

## 与现有系统的关系

- Node.js 脚本**继续保留**，作为数据采集层
- Python 系统专注数据存储 + 策略分析
- 共享数据目录 `~/.ohmyyangjibao/`
- 不改动现有 tools/ 目录下的文件

## 开发顺序

1. `db.py` + `schema.sql` — 数据库初始化
2. `import_funds.py` — 基金列表导入
3. `import_nav.py` — 净值数据导入
4. `backtest.py` — 回测引擎核心
5. `strategies.py` — 内置策略
6. `report.py` — 报告输出
7. `cli.py` — 命令行入口
8. `update.py` — 数据更新
