# AI-Native 基金分析助手 — 设计文档

> **核心转变**: 从"用户导航页面"到"AI 代理驱动的对话式分析"
> **架构**: Chat-First 前端 + Tool-Use 后端 + DeepSeek Function Calling

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────────┐
│                   前端 (React)                       │
│  ┌──────────┐  ┌──────────────────────────────────┐ │
│  │ 左侧面板  │  │         AI 对话主面板             │ │
│  │ 历史会话  │  │  ┌────────────────────────────┐  │ │
│  │ 快捷入口  │  │  │ ChatMessage 流式渲染         │  │ │
│  │ 一键分析  │  │  │  ├── TextBlock (文字)       │  │ │
│  │          │  │  │  ├── EChartsBlock (图表)    │  │ │
│  │          │  │  │  ├── TableBlock (表格)      │  │ │
│  │          │  │  │  └── FundCardBlock (卡片)   │  │ │
│  │          │  │  └────────────────────────────┘  │ │
│  │          │  │  ┌────────────────────────────┐  │ │
│  │          │  │  │ InputBar + QuickActions    │  │ │
│  │          │  │  └────────────────────────────┘  │ │
│  └──────────┘  └──────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────┘
                       │ SSE /chat/stream
                       ▼
┌─────────────────────────────────────────────────────┐
│                后端 (Spring Boot)                    │
│  ┌──────────────────────────────────────────────┐   │
│  │          AIChatService                       │   │
│  │  POST /api/ai/chat → DeepSeek + Tool-Use    │   │
│  │  1. 接收消息 → 查历史 → 调 DeepSeek         │   │
│  │  2. DeepSeek 返回 tool_call → 执行工具      │   │
│  │  3. 结果送回 DeepSeek → 合成回答            │   │
│  │  4. SSE 流式返回给前端                      │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  ┌───────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ToolRegistry│ │ToolExecutor│ │Tool 实例集合      │  │
│  │注册所有工具 │ │执行工具调用│ │search_funds     │  │
│  │name/desc/ │ │参数校验  │ │compare_funds    │  │
│  │param schema│ │异常处理  │ │get_portfolio... │  │
│  └───────────┘ └──────────┘ └──────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐   │
│  │    现有 Service 层 (不变)                     │   │
│  │  FundService / HoldingService / NewsService  │   │
│  │  IndustryAnalysisService / FundFlowService   │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  ┌──────────────┐  ┌──────────────┐                │
│  │ Conversation │  │ Message      │                │
│  │ (会话表)      │  │ (消息表)      │                │
│  └──────────────┘  └──────────────┘                │
└─────────────────────────────────────────────────────┘
```

---

## 2. 数据模型

### conversation（会话）

```sql
CREATE TABLE conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,          -- AI 自动生成标题
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### message（消息）

```sql
CREATE TABLE message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,             -- 所属会话
    role            VARCHAR(20) NOT NULL,        -- user / assistant / tool
    content         TEXT,                        -- 文字内容 (Markdown)
    tool_calls      JSON,                       -- [{name, arguments, result}]
    render_blocks   JSON,                       -- [{type, ...}] 富内容
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
```

### render_blocks 格式示例

AI 返回的富内容不与 Markdown 混在一起，而是结构化存储，前端独立渲染：

```json
[
  {"type": "echarts", "option": { "xAxis": {...}, "series": [...] }},
  {"type": "table", "columns": [{...}], "dataSource": [...]},
  {"type": "fund-cards", "funds": [{code, name, nav, change}...]}
]
```

---

## 3. Tool System

### 工具注册（Java）

每个工具是一个 Spring Bean，实现 `AiTool` 接口：

```java
public interface AiTool {
    String getName();           // 工具名 search_funds
    String getDescription();    // DeepSeek 理解用
    Map<String, Object> getParameters();  // JSON Schema
    Object execute(Map<String, Object> args);
}
```

### 工具清单

| 工具名 | 描述 | 参数 | 映射的后端方法 |
|--------|------|------|---------------|
| `search_funds` | 搜索基金列表 | keyword, type, company, page, size | FundService.listFunds |
| `get_fund_detail` | 获取单只基金详情 | code | FundService.getFundByCode |
| `compare_funds` | 多基金核心指标对比 | codes (逗号分隔) | FundController.compareFunds |
| `get_nav_history` | 获取基金历史净值 | code, days (可选) | FundService.getNavHistory |
| `get_portfolio` | 获取当前持仓 | 无 | HoldingService.getHoldings |
| `get_portfolio_summary` | 持仓盈亏汇总 | 无 | AnalysisService |
| `get_transactions` | 交易记录 | page, size | TransactionService |
| `get_market_news` | 市场新闻简报 | 无 | NewsService.getMarketBriefing |
| `get_industry_analysis` | 行业板块分析 | 无 | IndustryAnalysisService |
| `get_index_valuation` | 主要指数估值 | 无 | IndexValuationService.getValuations |
| `get_fund_flow` | 资金流向 | 无 | FundFlowService.getFundFlowList |
| `analyze_profit` | 收益分析 | 无 | AnalysisService.analyze |

### 调用流程

```
用户: "最近新能源基金怎么样？"

Round 1:
  → DeepSeek: tool_call search_funds(keyword="新能源")
  → 执行: fundService.listFunds("新能源") → 8只基金
  → DeepSeek: tool_call get_nav_history(code="005827")
  → 执行: navHistoryMapper.findByFundCode("005827")

Round 2:
  → DeepSeek: 合成回答
  → 输出: 文字分析 + ECharts走势图 + 推荐列表
```

---

## 4. API 设计

### 对话接口（SSE 流式）

```
POST /api/ai/chat
Content-Type: application/json
Accept: text/event-stream

{
  "conversationId": 1,        // 可选，null=新建会话
  "message": "帮我看看我的持仓"
}

Response (SSE):
data: {"type": "title", "content": "持仓分析"}
data: {"type": "text", "content": "你的持仓共3只基金..."}
data: {"type": "tool_call", "name": "get_portfolio", "args": {}}
data: {"type": "render_block", "block": {"type":"table", ...}}
data: {"type": "text", "content": "其中表现最好的是..."}
data: {"type": "render_block", "block": {"type":"echarts", ...}}
data: {"type": "done"}
data: {"type": "conversation_id", "id": 1}
```

### 历史会话接口

```
GET  /api/ai/conversations           → 会话列表
GET  /api/ai/conversations/{id}      → 会话详情+消息列表
DELETE /api/ai/conversations/{id}    → 删除会话
```

---

## 5. 前端组件结构

```
src/
├── pages/
│   ├── AIChat.tsx              ← 新的首页（对话主界面）
│   ├── FundMarket.tsx          ← 保留但降级为二级页面
│   ├── FundDetail.tsx          ← 保留
│   └── ...                     ← 其他页面保留，可通过对话内链接访问
│
├── components/
│   ├── chat/
│   │   ├── ChatPanel.tsx       → 左侧面板（历史会话 + 快捷入口）
│   │   ├── ChatMessages.tsx    → 消息列表 + 自动滚动
│   │   ├── ChatInput.tsx       → 输入框 + 快捷建议按钮
│   │   └── blocks/             ← render_blocks 渲染器
│   │       ├── TextBlock.tsx         → Markdown 渲染
│   │       ├── EChartsBlock.tsx      → echarts-for-react 包装
│   │       ├── TableBlock.tsx        → Ant Design Table 包装
│   │       └── FundCardsBlock.tsx    → 基金卡片列表
│   │
│   ├── Layout.tsx              → 改造：默认显示 ChatPanel + AIChat
│   └── QuickActions.tsx        → 一键分析按钮组
│
└── api/
    ├── ai.ts                   → SSE 流式接口封装 + EventSource
    ├── fund.ts                 ← 不变
    └── market.ts               ← 不变
```

### 关键组件逻辑

**ChatInput.tsx** — 输入框 + 快捷建议：
- 输入框用 uncontrolled ref 模式避免 IME 问题
- 底部显示 4-6 个快捷建议按钮（"今日市场"、"分析持仓"、"热门基金"、"指数估值"）
- 每次对话结束后 AI 动态生成新的建议

**EChartsBlock.tsx** — 通用图表渲染：
- 接收完整的 ECharts option 对象（由 AI 生成）
- 渲染 echarts-for-react
- 自动 resize

**TableBlock.tsx** — 通用表格渲染：
- 接收 columns + dataSource
- 渲染 Ant Design Table
- 点击行可查看基金详情（链接）

---

## 6. 布局改造

```
新版 Layout:
┌──────────────────────────────────────────────────┐
│ Header: Logo + "AI 基金助手" + 同步按钮           │
├──────────┬───────────────────────────────────────┤
│ 左侧面板  │  右侧内容区                          │
│ (240px)  │                                       │
│          │  默认: AIChat.tsx 对话界面              │
│ 快捷入口  │  传统页面: 通过菜单切换                 │
│  ├ 对话   │  但不再推荐——对话是主要交互             │
│  ├ 基金   │                                       │
│  ├ 持仓   │                                       │
│  ├ 更多   │                                       │
│          │                                       │
│ 历史会话  │                                       │
│ 列表     │                                       │
│          │                                       │
│ 一键分析  │                                       │
│  ├ 今日   │                                       │
│  ├ 持仓   │                                       │
│  └ 行业   │                                       │
└──────────┴───────────────────────────────────────┘
```

### 快捷入口说明

左侧面板包含：
- **快捷入口**：对话 / 基金市场 / 持仓 / 交易记录（点击跳转到传统页面）
- **一键分析**：固定的 3 个快捷操作按钮 → 发送预置 prompt
  - "今日市场回顾" → 调新闻+指数+资金流向 AI 汇总
  - "分析我的持仓" → 调持仓+收益 AI 分析
  - "行业板块分析" → 调行业数据 AI 解读
- **历史会话**：最近 10 条会话列表，点击恢复上下文

---

## 7. 实施路线

### Phase 1 — 基础设施 (核心骨架)
- Conversation / Message 实体 + Mapper + 数据库表
- AiTool 接口 + ToolRegistry + ToolExecutor
- AIChatService（对话管理 + DeepSeek function calling）
- SSE 流式 API `/api/ai/chat`

### Phase 2 — 工具实现
- 注册 12 个 AiTool 实现
- 每个工具的参数 schema 定义
- 工具调用异常处理 + 超时控制

### Phase 3 — 前端 Chat 界面
- ChatPanel + ChatMessages + ChatInput
- SSE EventSource 封装
- render_blocks 渲染器（Text / ECharts / Table / FundCards）
- Layout 改造

### Phase 4 — 快捷功能 + 体验优化
- 一键分析按钮
- 历史会话恢复
- AI 动态建议
- 流式加载动画
- 错误重试
