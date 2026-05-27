# 老大 我们这样做基金真的能涨么

**OhMyYangJiBao** — 个人 A 股基金投资分析助手，基于 Claude Code 技能，零依赖抓取天天基金/东方财富/腾讯行情公开数据。

![preview](preview.png)

## 快速开始

```bash
# 查询基金
node tools/search-funds.js --keyword=易方达
node tools/get-fund-detail.js --code=001856
node tools/get-fund-performance.js --code=001856

# 市场行情
node tools/get-index-valuation.js
node tools/get-market-news.js

# 持仓分析
node tools/get-fund-holdings.js --code=001856
node tools/compare-funds.js --codes=001856,005844
```

## 项目结构

```
├── skill.md              # Claude Code 技能定义
├── package.json          # Node.js ESM 配置
├── README.md
├── .gitignore
├── .claude/
│   └── skills/
│       └── oh-my-yang-ji-bao/   # Claude Code 发现入口（符号链接）
└── tools/                       # 全部工具脚本
    ├── api.js                               # 公共模块（HTTP/JSONP/缓存）
    ├── search-funds.js                      # 搜索基金
    ├── get-fund-detail.js                   # 基金详情
    ├── get-fund-performance.js              # 阶段收益率
    ├── get-fund-holdings.js                 # 前十大持仓
    ├── get-fund-manager.js                  # 基金经理
    ├── get-fund-fees.js                     # 费率
    ├── get-fund-risk-metrics.js             # 风险指标
    ├── get-nav-history.js                   # 历史净值
    ├── compare-funds.js                     # 基金对比
    ├── get-index-valuation.js               # 指数估值
    ├── get-industry-analysis.js             # 行业板块
    ├── get-market-news.js                   # 财经新闻
    ├── get-fund-flow.js                     # 资金流向
    ├── get-fund-rankings.js                 # 基金排行
    ├── get-top-funds.js                     # 热门推荐
    ├── get-portfolio.js                     # 持仓管理
    ├── get-portfolio-summary.js             # 持仓汇总
    ├── get-transactions.js                  # 交易记录
    ├── analyze-profit.js                    # 收益分析
    ├── analyze-portfolio-risk.js            # 持仓风险评估
    └── simulate-drip.js                     # 定投模拟
```

## 数据源

| 数据 | 来源 |
|------|------|
| 基金代码列表 | `fund.eastmoney.com` |
| 实时净值 | `fundgz.1234567.com.cn` |
| 历史净值 | `api.fund.eastmoney.com` |
| 基金详情/F10 | `fundf10.eastmoney.com` |
| 财经新闻 | `finance.eastmoney.com` |
| 指数行情 | `qt.gtimg.cn` |

## 环境

- Node.js **v18+** 或 Bun
- 无需 `npm install`，零依赖

## 安装为 Claude Code 技能

```bash
ln -s "$PWD" ~/.claude/skills/oh-my-yang-ji-bao
```
