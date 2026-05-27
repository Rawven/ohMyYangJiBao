# 养基宝类基金跟踪应用 — 设计文档

## 概述

构建一个类似"养基宝"的公募基金投资跟踪应用，帮助用户管理基金自选、持仓、交易记录和收益分析。

## 技术栈

| 层 | 选型 | 理由 |
|---|------|------|
| 前端框架 | React 18 + TypeScript + Vite | 成熟生态、性能好 |
| UI 组件库 | Ant Design 5 | 国内生态、适合数据密集型场景 |
| 图表 | ECharts | 专业金融图表支持 |
| 状态管理 | React Query + Zustand | 服务端状态/客户端状态分离 |
| 后端 | Spring Boot 3 + MyBatis-Plus | 利用已有 Java 经验、企业级 |
| 数据库 | H2（开发）/ MySQL（生产） | H2 零配置启动、MySQL 可扩展 |
| 构建 | Maven | 标准 Java 项目构建 |

## 功能模块

### MVP 范围

1. **基金自选** — 添加/移除自选基金，列表展示涨跌、净值、日涨幅
2. **持仓管理** — 记录持仓份额、成本净值，自动计算市值、盈亏
3. **交易记录** — 买入/卖出流水增删查，自动更新持仓数据
4. **收益分析** — 持仓盈亏概览、收益率曲线、持仓分布饼图
5. **基金详情** — 基金基本信息、近三月/近一年净值走势图

### 非功能需求

- 数据：内置 Mock 数据种子（约 10 只典型公募基金），脱网可用
- 响应：页面加载 < 2s（Mock 数据）
- 兼容：Chrome/Firefox/Safari 最新版本

## 前端设计

### 页面路由

| 路由 | 页面 | 内容 |
|------|------|------|
| `/` | Dashboard 总览 | 持仓总市值、总盈亏、各基金收益卡片 |
| `/funds` | 基金市场 | 全部基金列表、搜索、自选标记 |
| `/funds/:code` | 基金详情 | 基本信息、净值走势图、历史表现 |
| `/portfolio` | 我的持仓 | 持仓列表、盈亏明细 |
| `/transactions` | 交易记录 | 所有买入/卖出流水、筛选 |
| `/analysis` | 收益分析 | 收益率曲线图、持仓分布饼图 |

### 状态管理

- **React Query**: 管理所有服务端数据（基金列表、持仓、交易），自动缓存/刷新
- **Zustand**: 管理客户端状态（当前选中的基金、UI 偏好）

### 组件树

```
App
├── Layout (AntD ProLayout)
│   ├── SiderMenu (导航菜单)
│   └── Content
│       ├── DashboardPage
│       │   ├── AssetSummaryCard (总资产概览)
│       │   ├── ProfitSummaryCard (收益概览)
│       │   └── HoldingList (持仓简表)
│       ├── FundMarketPage
│       │   ├── FundSearchBar
│       │   ├── FundTable (基金列表表格)
│       │   └── FundActions (自选按钮)
│       ├── FundDetailPage
│       │   ├── FundInfo (基本信息)
│       │   └── NavChart (净值走势图)
│       ├── PortfolioPage
│       │   ├── PortfolioSummary (持仓汇总)
│       │   └── HoldingTable (持仓明细表)
│       ├── TransactionPage
│       │   ├── TransactionFilter
│       │   ├── TransactionTable
│       │   └── AddTransactionModal
│       └── AnalysisPage
│           ├── ProfitChart (收益曲线)
│           └── DistributionChart (分布饼图)
```

## 后端设计

### API 接口

```
GET    /api/funds              — 基金列表（含搜索、筛选）
GET    /api/funds/{code}       — 基金详情
GET    /api/funds/{code}/nav   — 净值历史

GET    /api/holdings           — 持仓列表
PUT    /api/holdings/{id}      — 更新持仓

GET    /api/transactions       — 交易记录列表
POST   /api/transactions       — 新增交易
DELETE /api/transactions/{id}  — 删除交易

GET    /api/analysis/profit    — 收益分析数据
GET    /api/analysis/distribution — 持仓分布
```

### 数据模型

```
Fund
  - id: Long (PK)
  - code: String (基金代码，唯一)
  - name: String (基金名称)
  - type: String (股票型/债券型/混合型/货币型)
  - nav: BigDecimal (最新净值)
  - navDate: LocalDate (净值日期)
  - dayIncrease: BigDecimal (日涨幅 %)
  - establishDate: LocalDate (成立日期)
  - company: String (基金公司)

NavHistory
  - id: Long (PK)
  - fundCode: String (基金代码)
  - nav: BigDecimal (净值)
  - date: LocalDate (日期)
  - unique: (fundCode, date)

Holding
  - id: Long (PK)
  - fundCode: String
  - fundName: String
  - shares: BigDecimal (持有份额)
  - costNav: BigDecimal (成本净值)
  - currentNav: BigDecimal (当前净值，非持久化)
  - marketValue: BigDecimal (市值，非持久化)
  - profit: BigDecimal (盈亏，非持久化)
  - profitRate: BigDecimal (收益率，非持久化)

Transaction
  - id: Long (PK)
  - fundCode: String
  - type: String (BUY/SELL)
  - amount: BigDecimal (交易金额)
  - nav: BigDecimal (交易净值)
  - shares: BigDecimal (交易份额)
  - transactionDate: LocalDateTime
  - note: String (备注)
```

### 项目结构（后端）

```
fund-tracker-server/
├── src/main/java/com/fundtracker/
│   ├── FundTrackerApplication.java
│   ├── controller/
│   │   ├── FundController.java
│   │   ├── HoldingController.java
│   │   ├── TransactionController.java
│   │   └── AnalysisController.java
│   ├── service/
│   │   ├── FundService.java
│   │   ├── HoldingService.java
│   │   ├── TransactionService.java
│   │   └── AnalysisService.java
│   ├── model/
│   │   ├── entity/
│   │   │   ├── Fund.java
│   │   │   ├── NavHistory.java
│   │   │   ├── Holding.java
│   │   │   └── Transaction.java
│   │   ├── dto/
│   │   │   ├── FundDTO.java
│   │   │   └── AnalysisDTO.java
│   │   └── vo/
│   │       └── ApiResponse.java
│   ├── mapper/
│   │   ├── FundMapper.java
│   │   ├── NavHistoryMapper.java
│   │   ├── HoldingMapper.java
│   │   └── TransactionMapper.java
│   └── config/
│       └── CorsConfig.java
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── data.sql
└── pom.xml
```

### 项目结构（前端）

```
fund-tracker-web/
├── src/
│   ├── App.tsx
│   ├── main.tsx
│   ├── api/
│   │   ├── client.ts (axios 实例)
│   │   ├── fund.ts
│   │   ├── holding.ts
│   │   ├── transaction.ts
│   │   └── analysis.ts
│   ├── store/
│   │   └── uiStore.ts (Zustand)
│   ├── pages/
│   │   ├── Dashboard.tsx
│   │   ├── FundMarket.tsx
│   │   ├── FundDetail.tsx
│   │   ├── Portfolio.tsx
│   │   ├── Transactions.tsx
│   │   └── Analysis.tsx
│   ├── components/
│   │   ├── Layout.tsx
│   │   ├── FundTable.tsx
│   │   ├── HoldingTable.tsx
│   │   ├── TransactionTable.tsx
│   │   ├── AddTransactionModal.tsx
│   │   ├── NavChart.tsx
│   │   ├── ProfitChart.tsx
│   │   └── DistributionChart.tsx
│   ├── types/
│   │   └── index.ts
│   └── utils/
│       └── format.ts
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## 数据流

1. 启动时 H2 执行 `schema.sql` 建表 + `data.sql` 插入种子数据
2. 前端 React Query 自动请求后端 API 获取数据
3. 添加交易 → POST API → 后端写入 DB → 前端 invalidate queries → 自动刷新
4. 持仓市值/盈亏 = 实时计算（当前净值 × 份额 — 成本）

## CORS 配置

后端配置全局 CORS，允许 localhost 开发服务器跨域访问。
