# 老大 我们这样做基金真的能涨么

**OhMyYangJiBao** — 个人 A 股基金投资分析助手，基于 Claude Code 技能，零依赖抓取天天基金/东方财富/腾讯行情公开数据。

![preview](preview.png)

## 快速上手

```bash
# 一键基金分析（推荐入口）
node tools/analyze-fund.mjs --code=001856

# 搜索基金
node tools/search-funds.mjs --keyword=易方达

# 市场行情
node tools/get-index-valuation.mjs                        # 指数估值
node tools/get-industry-analysis.mjs --industry=半导体     # 行业板块
node tools/get-fund-rankings.mjs --type=混合型 --topN=10  # 基金排行

# 基金对比
node tools/compare-funds.mjs --codes=001856,005844

# 定投模拟
node tools/simulate-drip.mjs --code=110011 --amount=1000 --months=12
```

## 工具一览

### 综合查询
| 工具 | 用途 |
|------|------|
| `analyze-fund.mjs` | **一键分析**：净值+持仓+业绩+经理+费率+风险并行获取 |
| `search-funds.mjs` | 按名称/代码/类型搜索 10000+ 基金 |
| `compare-funds.mjs` | 多只基金详情+持仓对比 |

### 基金详情
| 工具 | 用途 |
|------|------|
| `get-fund-detail.mjs` | 净值/类型/基金公司/成立日期 |
| `get-fund-holdings.mjs` | 前十大持仓股票 |
| `get-fund-manager.mjs` | 基金经理姓名 |
| `get-fund-fees.mjs` | 管理费/托管费/销售服务费 |

### 业绩与风险
| 工具 | 用途 |
|------|------|
| `get-fund-performance.mjs` | 近1周/1月/3月/6月/1年/3年/今年以来 |
| `get-fund-risk-metrics.mjs` | 最大回撤/年化波动率/胜率 |
| `get-nav-history.mjs` | 历史净值明细 |

### 市场行情
| 工具 | 用途 |
|------|------|
| `get-index-valuation.mjs` | 主要指数 PE/PB 估值百分位 |
| `get-industry-analysis.mjs` | 行业板块涨跌排行 + 搜索相关基金 |
| `get-fund-rankings.mjs` | 按类型/阶段涨幅真实排行 |
| `get-fund-scale.mjs` | 基金规模变化 & 净申购排名 |
| `get-market-news.mjs` | 财经头条新闻 |

### 用户持仓
| 工具 | 用途 |
|------|------|
| `get-portfolio.mjs` | 添加/查看持仓 |
| `get-portfolio-summary.mjs` | 总市值/成本/盈亏/分布 |
| `analyze-portfolio-risk.mjs` | 集中度/行业暴露/风险评级 |
| `get-transactions.mjs` | 交易记录管理 |
| `simulate-drip.mjs` | 定投收益模拟 |

## 数据源

| 数据 | 来源 |
|------|------|
| 基金代码列表 | `fund.eastmoney.com` |
| 实时净值 | `fundgz.1234567.com.cn` |
| 历史净值 | `api.fund.eastmoney.com` |
| 基金详情/F10 | `fundf10.eastmoney.com` |
| 财经新闻 | `finance.eastmoney.com` |
| 指数行情 | `qt.gtimg.cn` |
| 行业板块 | `push2.eastmoney.com` |
| 基金排行 | `fund.eastmoney.com/data/rankhandler.aspx` |

## 项目结构

```
├── skill.md          # Claude Code 技能定义
├── README.md
├── .gitignore
├── preview.png
└── tools/            # 21 个 .mjs 工具脚本
    ├── api.mjs       # 公共模块（HTTP/JSONP/缓存）
    └── *.mjs         # 各功能脚本（见上方工具一览）
```

## 环境要求

- **Node.js v18+** 或 **Bun**
- **零依赖** — 无需 `npm install`
- 持仓数据存储在 `~/.ohmyyangjibao/`

## 安装为 Claude Code 技能

```bash
ln -s "$PWD" ~/.claude/skills/oh-my-yang-ji-bao
```

安装后在 Claude Code 中通过 `/oh-my-yang-ji-bao` 即可调用。
