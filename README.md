# 老大 我们这样做基金真的能涨么

**OhMyYangJiBao** — 个人 A 股基金投资分析助手，基于 Claude Code 技能，零依赖抓取天天基金/东方财富/腾讯行情公开数据。

![preview](preview.png)

## 快速开始

```bash
# 综合查询（推荐）
node tools/analyze-fund.mjs --code=001856

# 搜索基金
node tools/search-funds.mjs --keyword=易方达

# 基金详情
node tools/get-fund-detail.mjs --code=001856

# 阶段收益
node tools/get-fund-performance.mjs --code=001856

# 持仓分析
node tools/get-fund-holdings.mjs --code=001856

# 对比多只基金
node tools/compare-funds.mjs --codes=001856,005844

# 市场行情
node tools/get-index-valuation.mjs
node tools/get-market-news.mjs

# 基金排行
node tools/get-fund-rankings.mjs --type=混合型 --topN=10

# 行业板块
node tools/get-industry-analysis.mjs
```

## 项目结构

```
├── skill.md              # Claude Code 技能定义
├── README.md
├── .gitignore
├── tools/                # 全部工具脚本（.mjs ESM）
│   ├── api.js → api.mjs               # 公共模块（HTTP/JSONP/缓存）
│   ├── analyze-fund.mjs                # 一键基金分析（推荐）
│   ├── search-funds.mjs                # 搜索基金
│   ├── get-fund-detail.mjs             # 基金详情
│   ├── get-fund-performance.mjs        # 阶段收益率
│   ├── get-fund-holdings.mjs           # 前十大持仓
│   ├── get-fund-manager.mjs            # 基金经理
│   ├── get-fund-fees.mjs               # 费率
│   ├── get-fund-risk-metrics.mjs       # 风险指标
│   ├── get-nav-history.mjs             # 历史净值
│   ├── compare-funds.mjs               # 基金对比
│   ├── get-index-valuation.mjs         # 指数估值
│   ├── get-industry-analysis.mjs       # 行业板块
│   ├── get-market-news.mjs             # 财经新闻
│   ├── get-fund-rankings.mjs           # 基金排行
│   ├── get-fund-scale.mjs              # 资金流向/规模变化
│   ├── get-portfolio.mjs               # 持仓管理
│   ├── get-portfolio-summary.mjs       # 持仓汇总
│   ├── get-transactions.mjs            # 交易记录
│   ├── analyze-profit.mjs              # 收益分析
│   ├── analyze-portfolio-risk.mjs      # 持仓风险评估
│   └── simulate-drip.mjs               # 定投模拟
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
