# AI-Native 基金分析助手 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将基金跟踪系统从"传统后台管理"改造为"AI 对话驱动的个人基金分析助手"

**Architecture:** Chat-First 前端 + Tool-Use 后端。后端将 DeepSeekService 升级为带 function calling 的 Agent，每个数据接口注册为 tool。前端以 AI 对话面板为主界面，文字/图表/表格通过 render_blocks 结构化渲染。

**Tech Stack:** Spring Boot 3.2 + MyBatis-Plus + H2 + DeepSeek API (function calling) + SseEmitter + React 18 + Ant Design 5 + ECharts 5 + Zustand + @tanstack/react-query

---

## 文件清单

### 后端新建
- `model/entity/Conversation.java` — 会话实体
- `model/entity/Message.java` — 消息实体
- `mapper/ConversationMapper.java` — 会话 Mapper
- `mapper/MessageMapper.java` — 消息 Mapper
- `service/AiTool.java` — 工具接口
- `service/AiToolRegistry.java` — 工具注册中心
- `service/tool/*.java` — 12 个具体工具实现
- `service/AIChatService.java` — 对话服务（Agent 核心）
- `controller/AIChatController.java` — SSE 流式接口

### 后端修改
- `service/DeepSeekService.java` — 增加 function calling + 流式支持
- `resources/schema.sql` — 增加 conversation + message 表

### 前端新建
- `pages/AIChat.tsx` — 对话主页面
- `components/chat/ChatPanel.tsx` — 左侧面板
- `components/chat/ChatMessages.tsx` — 消息列表
- `components/chat/ChatInput.tsx` — 输入框
- `components/chat/blocks/TextBlock.tsx` — 文字块
- `components/chat/blocks/EChartsBlock.tsx` — 图表块
- `components/chat/blocks/TableBlock.tsx` — 表格块
- `components/chat/blocks/FundCardsBlock.tsx` — 基金卡片块
- `api/ai.ts` — SSE 流式接口封装
- `store/chatStore.ts` — 对话状态管理

### 前端修改
- `App.tsx` — 增加路由
- `components/Layout.tsx` — 改造为 Chat-First 布局

---

### Task 1: 数据模型 — Conversation + Message 实体

**Files:**
- Create: `model/entity/Conversation.java`
- Create: `model/entity/Message.java`
- Create: `mapper/ConversationMapper.java`
- Create: `mapper/MessageMapper.java`
- Modify: `resources/schema.sql:53-70`

- [ ] **Step 1: 添加 DDL 到 schema.sql**

在文件末尾追加：

```sql
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls JSON,
    render_blocks JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
```

- [ ] **Step 2: 创建 Conversation 实体**

`model/entity/Conversation.java`:

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("conversation")
public class Conversation {
    @TableId
    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: 创建 Message 实体**

`model/entity/Message.java`:

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("message")
public class Message {
    @TableId
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String toolCalls;
    private String renderBlocks;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolCalls() { return toolCalls; }
    public void setToolCalls(String toolCalls) { this.toolCalls = toolCalls; }
    public String getRenderBlocks() { return renderBlocks; }
    public void setRenderBlocks(String renderBlocks) { this.renderBlocks = renderBlocks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 创建 Mapper 接口**

`mapper/ConversationMapper.java`:
```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {}
```

`mapper/MessageMapper.java`:
```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {}
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-server && mvn compile -q
```
Expected: 无输出（编译成功）

---

### Task 2: Tool 系统 — 接口 + 注册中心 + 基础实现

**Files:**
- Create: `service/AiTool.java`
- Create: `service/AiToolRegistry.java`

- [ ] **Step 1: 创建 AiTool 接口**

`service/AiTool.java`:

```java
package com.fundtracker.service;

import java.util.Map;

public interface AiTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();  // JSON Schema for function calling
    Object execute(Map<String, Object> args);
}
```

- [ ] **Step 2: 创建 AiToolRegistry**

`service/AiToolRegistry.java`:

```java
package com.fundtracker.service;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiToolRegistry {
    private final Map<String, AiTool> tools = new HashMap<>();

    public AiToolRegistry(List<AiTool> toolList) {
        toolList.forEach(t -> tools.put(t.getName(), t));
    }

    public AiTool getTool(String name) {
        return tools.get(name);
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return tools.values().stream().map(t -> Map.of(
            "type", "function",
            "function", Map.of(
                "name", t.getName(),
                "description", t.getDescription(),
                "parameters", t.getParameters()
            )
        )).toList();
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```

---

### Task 3: DeepSeekService 升级 — Function Calling + 流式支持

**Files:**
- Modify: `service/DeepSeekService.java` (全量重写)

- [ ] **Step 1: 重写 DeepSeekService**

`service/DeepSeekService.java`:

```java
package com.fundtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DeepSeekService {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiToolRegistry toolRegistry;

    public DeepSeekService(@Value("${deepseek.api-key}") String apiKey,
                           @Value("${deepseek.base-url}") String baseUrl,
                           AiToolRegistry toolRegistry) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.toolRegistry = toolRegistry;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 非流式调用（保留给原有 analyzeHoldings 使用）
     */
    public String callDeepSeekWithPrompt(String userMessage) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", "你是一个专业的A股基金投资分析助手，回答简洁专业，使用中文。"),
                    Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", 1024,
                "temperature", 0.7,
                "stream", false
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").get(0);
            if (choice != null) {
                return choice.path("message").path("content").asText("分析服务暂时不可用");
            }
            return "分析服务暂时不可用";
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage());
            return "分析服务调用失败：" + e.getMessage();
        }
    }

    /**
     * 流式 Function Calling 对话
     * 返回事件的回调: onText(text), onToolCall(name,args), onDone()
     */
    public void chatStream(List<Map<String, Object>> messages,
                           ChatCallback callback) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", true);
        requestBody.put("tools", toolRegistry.getToolDefinitions());

        String json = objectMapper.writeValueAsString(requestBody);
        log.debug("DeepSeek 请求: {}", json);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        InputStream body = response.body();

        // 解析 SSE 流
        Scanner scanner = new Scanner(body, "UTF-8");
        StringBuilder contentBuffer = new StringBuilder();
        String currentToolName = null;
        StringBuilder currentToolArgs = new StringBuilder();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.startsWith("data: ")) continue;
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) break;

            try {
                JsonNode chunk = objectMapper.readTree(data);
                JsonNode delta = chunk.path("choices").get(0).path("delta");

                // 文字片段
                if (delta.has("content") && !delta.path("content").isNull()) {
                    String text = delta.path("content").asText();
                    contentBuffer.append(text);
                    callback.onText(text);
                }

                // tool_calls 片段
                if (delta.has("tool_calls")) {
                    JsonNode toolCall = delta.path("tool_calls").get(0);
                    if (toolCall != null) {
                        if (toolCall.has("function")) {
                            JsonNode func = toolCall.path("function");
                            if (func.has("name") && !func.path("name").isNull()) {
                                currentToolName = func.path("name").asText();
                            }
                            if (func.has("arguments") && !func.path("arguments").isNull()) {
                                currentToolArgs.append(func.path("arguments").asText());
                            }
                        }
                        if (toolCall.has("id")) {
                            // tool_call 完成
                            if (currentToolName != null && currentToolArgs.length() > 0) {
                                callback.onToolCall(currentToolName, currentToolArgs.toString());
                                currentToolName = null;
                                currentToolArgs = new StringBuilder();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 SSE 块失败: {}", e.getMessage());
            }
        }

        callback.onDone(contentBuffer.toString());
        body.close();
    }

    public interface ChatCallback {
        void onText(String text);
        void onToolCall(String name, String arguments);
        void onDone(String fullContent);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

---

### Task 4: 工具实现 — 12 个 AiTool

**Files:**
- Create: `service/tool/SearchFundsTool.java`
- Create: `service/tool/GetFundDetailTool.java`
- Create: `service/tool/CompareFundsTool.java`
- Create: `service/tool/GetNavHistoryTool.java`
- Create: `service/tool/GetPortfolioTool.java`
- Create: `service/tool/GetPortfolioSummaryTool.java`
- Create: `service/tool/GetTransactionsTool.java`
- Create: `service/tool/GetMarketNewsTool.java`
- Create: `service/tool/GetIndustryAnalysisTool.java`
- Create: `service/tool/GetIndexValuationTool.java`
- Create: `service/tool/GetFundFlowTool.java`
- Create: `service/tool/AnalyzeProfitTool.java`

- [ ] **Step 1: 创建工具目录**

```bash
mkdir -p /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-server/src/main/java/com/fundtracker/service/tool
```

- [ ] **Step 2: 实现工具基类辅助方法**

每个工具遵循相同模式。以 SearchFundsTool 为例：

`service/tool/SearchFundsTool.java`：

```java
package com.fundtracker.service.tool;

import com.fundtracker.service.AiTool;
import com.fundtracker.service.FundService;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class SearchFundsTool implements AiTool {
    private final FundService fundService;
    public SearchFundsTool(FundService fundService) { this.fundService = fundService; }

    @Override
    public String getName() { return "search_funds"; }

    @Override
    public String getDescription() {
        return "搜索基金列表，支持按关键字、类型、基金公司筛选";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "keyword", Map.of("type", "string", "description", "基金名称或代码关键字"),
                "type", Map.of("type", "string", "description", "基金类型，如 股票型、混合型、债券型、指数型"),
                "company", Map.of("type", "string", "description", "基金公司，如 易方达基金"),
                "page", Map.of("type", "integer", "description", "页码，从1开始"),
                "size", Map.of("type", "integer", "description", "每页数量")
            ),
            "required", List.of()
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String keyword = (String) args.getOrDefault("keyword", "");
        String type = (String) args.getOrDefault("type", "");
        String company = (String) args.getOrDefault("company", "");
        int page = args.containsKey("page") ? ((Number) args.get("page")).intValue() : 1;
        int size = args.containsKey("size") ? ((Number) args.get("size")).intValue() : 10;
        try {
            var pageResult = fundService.screenerQuery(
                keyword.isEmpty() ? null : keyword,
                type.isEmpty() ? null : type,
                company.isEmpty() ? null : company,
                null, null, null, null, null, page, size);
            return Map.of(
                "total", pageResult.getTotal(),
                "items", pageResult.getRecords().stream().map(f -> Map.of(
                    "code", f.getCode(),
                    "name", f.getName(),
                    "type", f.getType(),
                    "nav", f.getNav(),
                    "navDate", f.getNavDate() != null ? f.getNavDate().toString() : "",
                    "dayIncrease", f.getDayIncrease(),
                    "company", f.getCompany() != null ? f.getCompany() : ""
                )).toList()
            );
        } catch (Exception e) {
            return Map.of("error", "搜索失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: 实现剩余 11 个工具**

模式相同：注入对应 Service → getName/desc/params → execute 调用底层服务 → 返回 Map

各工具注入的 Service：

| 工具 | 注入 | 调用的方法 |
|------|------|-----------|
| `GetFundDetailTool` | FundService | `getFundByCode(code)` |
| `CompareFundsTool` | FundService, FundHoldingService | `getFundByCode` + `getHoldings` |
| `GetNavHistoryTool` | FundService | `getNavHistory(code)` |
| `GetPortfolioTool` | HoldingService | `listHoldingDTOs()` |
| `GetPortfolioSummaryTool` | AnalysisService | `getAnalysis()` |
| `GetTransactionsTool` | TransactionService | `listTransactions(p, s)` |
| `GetMarketNewsTool` | NewsService | `getMarketBriefing()` |
| `GetIndustryAnalysisTool` | IndustryAnalysisService | `getIndustryAnalysis()` |
| `GetIndexValuationTool` | IndexValuationService | `getValuations()` |
| `GetFundFlowTool` | FundFlowService | `getFundFlowList()` |
| `AnalyzeProfitTool` | AnalysisService | `getAnalysis()` |

- [ ] **Step 4: 编译验证**

```bash
mvn compile -q
```

---

### Task 5: AIChatService — 对话管理 + Agent 核心

**Files:**
- Create: `service/AIChatService.java`

- [ ] **Step 1: 创建 AIChatService**

`service/AIChatService.java`:

```java
package com.fundtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.mapper.ConversationMapper;
import com.fundtracker.mapper.MessageMapper;
import com.fundtracker.model.entity.Conversation;
import com.fundtracker.model.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AIChatService {
    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    private final DeepSeekService deepSeekService;
    private final AiToolRegistry toolRegistry;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是一个专业的个人基金投资分析助手。你的用户是A股基金的个人投资者。

        核心能力：
        1. 你可以通过工具查询基金数据、持仓、市场信息
        2. 根据数据给出分析建议，不提供具体的买卖建议
        3. 用专业但易懂的中文回答

        回复格式规则：
        1. 文字分析直接用 Markdown
        2. 需要展示图表时，在回复末尾添加 [ECHARTS: {json}]
        3. 需要展示表格时，在回复末尾添加 [TABLE: {json}]
        4. 需要展示基金卡片时，在回复末尾添加 [FUNDS: {json}]

        数据分析时尽量调用工具获取实时数据，不要凭记忆回答。
        """;

    public AIChatService(DeepSeekService deepSeekService, AiToolRegistry toolRegistry,
                         ConversationMapper conversationMapper, MessageMapper messageMapper,
                         ObjectMapper objectMapper) {
        this.deepSeekService = deepSeekService;
        this.toolRegistry = toolRegistry;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public List<Conversation> getConversations() {
        return conversationMapper.selectList(null);
    }

    public Conversation getConversation(Long id) {
        return conversationMapper.selectById(id);
    }

    public List<Message> getMessages(Long conversationId) {
        return messageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreatedAt)
        );
    }

    /**
     * 处理用户消息，返回 SSE 事件流
     */
    @Transactional
    public void processMessage(Long conversationId, String userMessage,
                               ChatEventListener listener) {
        try {
            // 1. 创建或获取会话
            if (conversationId == null) {
                Conversation conv = new Conversation();
                conv.setTitle(userMessage.length() > 50 ? userMessage.substring(0, 50) : userMessage);
                conv.setCreatedAt(LocalDateTime.now());
                conv.setUpdatedAt(LocalDateTime.now());
                conversationMapper.insert(conv);
                conversationId = conv.getId();
                listener.onConversationId(conversationId);
            }

            // 2. 保存用户消息
            Message userMsg = new Message();
            userMsg.setConversationId(conversationId);
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            userMsg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(userMsg);

            // 3. 构建消息历史
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            for (Message msg : getMessages(conversationId)) {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent() != null ? msg.getContent() : "");
                messages.add(m);
            }

            // 4. 预留 conversation_id 事件给前端
            listener.onConversationId(conversationId);

            // 5. 循环：DeepSeek 推理 → 可能调工具 → 再推理
            int maxRounds = 5;
            for (int round = 0; round < maxRounds; round++) {
                StringBuilder fullContent = new StringBuilder();
                List<Map<String, Object>> toolCallsCollected = new ArrayList<>();

                // 记录本轮是否调用了工具
                boolean[] hasToolCall = {false};

                deepSeekService.chatStream(messages, new DeepSeekService.ChatCallback() {
                    @Override
                    public void onText(String text) {
                        fullContent.append(text);
                        listener.onText(text);
                    }

                    @Override
                    public void onToolCall(String name, String arguments) {
                        hasToolCall[0] = true;
                        Map<String, Object> tc = new HashMap<>();
                        tc.put("name", name);
                        tc.put("arguments", arguments);
                        toolCallsCollected.add(tc);
                        listener.onToolCall(name, arguments);
                    }

                    @Override
                    public void onDone(String content) {
                        // handled below
                    }
                });

                // 保存 assistant 消息
                Message assistantMsg = new Message();
                assistantMsg.setConversationId(conversationId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(fullContent.toString());
                assistantMsg.setCreatedAt(LocalDateTime.now());
                messageMapper.insert(assistantMsg);

                if (!hasToolCall[0]) {
                    // 没有工具调用，对话结束
                    break;
                }

                // 执行工具调用并将结果加入消息历史
                for (Map<String, Object> tc : toolCallsCollected) {
                    String toolName = (String) tc.get("name");
                    String argsJson = (String) tc.get("arguments");

                    AiTool tool = toolRegistry.getTool(toolName);
                    if (tool == null) {
                        log.warn("未知工具: {}", toolName);
                        continue;
                    }

                    Map<String, Object> args;
                    try {
                        args = objectMapper.readValue(argsJson, Map.class);
                    } catch (Exception e) {
                        args = new HashMap<>();
                    }

                    Object result = tool.execute(args);
                    String resultJson;
                    try {
                        resultJson = objectMapper.writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        resultJson = "{\"error\":\"序列化失败\"}";
                    }

                    // 保存 tool 消息
                    Message toolMsg = new Message();
                    toolMsg.setConversationId(conversationId);
                    toolMsg.setRole("tool");
                    toolMsg.setContent(resultJson);
                    toolMsg.setCreatedAt(LocalDateTime.now());
                    messageMapper.insert(toolMsg);

                    // 加入本轮消息历史继续推理
                    messages.add(Map.of("role", "assistant", "content", null,
                        "tool_calls", List.of(Map.of(
                            "id", "call_" + round,
                            "type", "function",
                            "function", Map.of("name", toolName, "arguments", argsJson)
                        ))
                    ));
                    messages.add(Map.of("role", "tool",
                        "content", resultJson,
                        "tool_call_id", "call_" + round));
                }

                // 更新会话标题（第一条用户消息）
                Conversation conv = conversationMapper.selectById(conversationId);
                if (conv != null) {
                    conv.setUpdatedAt(LocalDateTime.now());
                    conversationMapper.updateById(conv);
                }
            }

            listener.onDone();

        } catch (Exception e) {
            log.error("AI 对话处理失败", e);
            listener.onError("处理失败: " + e.getMessage());
        }
    }

    public interface ChatEventListener {
        void onConversationId(Long id);
        void onText(String text);
        void onToolCall(String name, String arguments);
        void onDone();
        void onError(String error);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

---

### Task 6: AIChatController — SSE 流式接口

**Files:**
- Create: `controller/AIChatController.java`

- [ ] **Step 1: 创建 AIChatController**

`controller/AIChatController.java`:

```java
package com.fundtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.model.entity.Conversation;
import com.fundtracker.model.entity.Message;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.AIChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {
    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    private final AIChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AIChatController(AIChatService aiChatService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = "text/event-stream")
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        Long conversationId = body.get("conversationId") != null
            ? ((Number) body.get("conversationId")).longValue() : null;
        String message = (String) body.get("message");

        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout

        executor.submit(() -> {
            try {
                aiChatService.processMessage(conversationId, message, new AIChatService.ChatEventListener() {
                    @Override
                    public void onConversationId(Long id) {
                        send(Map.of("type", "conversation_id", "id", id));
                    }

                    @Override
                    public void onText(String text) {
                        send(Map.of("type", "text", "content", text));
                    }

                    @Override
                    public void onToolCall(String name, String arguments) {
                        send(Map.of("type", "tool_call", "name", name, "arguments", arguments));
                    }

                    @Override
                    public void onDone() {
                        send(Map.of("type", "done"));
                        emitter.complete();
                    }

                    @Override
                    public void onError(String error) {
                        send(Map.of("type", "error", "content", error));
                        emitter.completeWithError(new RuntimeException(error));
                    }

                    private void send(Object data) {
                        try {
                            emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(data)));
                        } catch (Exception e) {
                            log.warn("SSE 发送失败", e);
                        }
                    }
                });
            } catch (Exception e) {
                log.error("SSE 处理异常", e);
                try {
                    emitter.send(SseEmitter.event()
                        .data("{\"type\":\"error\",\"content\":\"服务器内部错误\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<Conversation>> getConversations() {
        return ApiResponse.success(aiChatService.getConversations());
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<List<Message>> getConversation(@PathVariable Long id) {
        return ApiResponse.success(aiChatService.getMessages(id));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        // Cascade delete messages then conversation
        var msgWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>();
        msgWrapper.eq(Message::getConversationId, id);
        // delete messages manually since H2 might not cascade
        com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper();
        // use mapper directly
        org.springframework.beans.factory.annotation.Autowired didn't work here, use constructor
        // Actually let me keep it simple - just delete conversation, messages orphaned
        var mapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>();
        mapper.eq(Message::getConversationId, id);
        // We need to inject MessageMapper... 
        // For simplicity: delete conversation only
        return ApiResponse.success(null);
    }
}
```

Wait, this has a problem with the delete endpoint - it doesn't have access to the mapper. Let me fix the controller to inject what it needs:

- [ ] **Step 2: 修正 delete 方法 — 在 AIChatService 中添加删除方法**

在 `AIChatService.java` 中添加：
```java
@Transactional
public void deleteConversation(Long id) {
    var msgWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>();
    msgWrapper.eq(Message::getConversationId, id);
    messageMapper.delete(msgWrapper);
    conversationMapper.deleteById(id);
}
```

修改 `AIChatController.java` 中的 delete 方法：
```java
@DeleteMapping("/conversations/{id}")
public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
    aiChatService.deleteConversation(id);
    return ApiResponse.success(null);
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```

---

### Task 7: 前端 Chat Store + SSE 封装

**Files:**
- Create: `store/chatStore.ts`
- Create: `api/ai.ts`

- [ ] **Step 1: 创建 chatStore**

`store/chatStore.ts`:

```typescript
import { create } from 'zustand'

export interface RenderBlock {
  type: 'text' | 'echarts' | 'table' | 'fund-cards'
  content?: string
  option?: any
  columns?: any[]
  dataSource?: any[]
  funds?: any[]
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  blocks?: RenderBlock[]
}

interface ChatState {
  conversationId: number | null
  messages: ChatMessage[]
  loading: boolean
  streaming: boolean

  setConversationId: (id: number) => void
  addMessage: (msg: ChatMessage) => void
  updateLastAssistant: (text: string, blocks?: RenderBlock[]) => void
  setLoading: (v: boolean) => void
  setStreaming: (v: boolean) => void
  reset: () => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  conversationId: null,
  messages: [],
  loading: false,
  streaming: false,

  setConversationId: (id) => set({ conversationId: id }),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  updateLastAssistant: (text, blocks) => set((s) => {
    const msgs = [...s.messages]
    const last = msgs[msgs.length - 1]
    if (last && last.role === 'assistant') {
      msgs[msgs.length - 1] = { ...last, content: text, blocks: blocks || last.blocks }
    } else {
      msgs.push({ id: Date.now().toString(), role: 'assistant', content: text, blocks })
    }
    return { messages: msgs }
  }),
  setLoading: (v) => set({ loading: v }),
  setStreaming: (v) => set({ streaming: v }),
  reset: () => set({ conversationId: null, messages: [], loading: false, streaming: false }),
}))
```

- [ ] **Step 2: 创建 SSE API 封装**

`api/ai.ts`:

```typescript
import { useChatStore } from '../store/chatStore'

export interface ChatResponse {
  conversationId: number | null
  messages: { role: string; content: string }[]
}

export function sendChatMessage(conversationId: number | null, message: string): EventSource {
  const store = useChatStore.getState()
  store.setStreaming(true)

  // Add user message immediately
  store.addMessage({ id: Date.now().toString(), role: 'user', content: message })

  // Add placeholder assistant message
  const assistantId = (Date.now() + 1).toString()
  store.addMessage({ id: assistantId, role: 'assistant', content: '' })

  // POST to SSE endpoint via fetch for proper streaming
  const controller = new AbortController()

  fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message }),
    signal: controller.signal,
  }).then(async (response) => {
    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    const decoder = new TextDecoder()
    let buffer = ''
    let fullText = ''
    const blocks: any[] = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data: ')) continue
        const data = line.slice(6).trim()
        if (!data) continue

        try {
          const event = JSON.parse(data)

          switch (event.type) {
            case 'conversation_id':
              useChatStore.getState().setConversationId(event.id)
              break
            case 'text':
              fullText += event.content
              useChatStore.getState().updateLastAssistant(fullText)
              break
            case 'tool_call':
              // Show tool call indicator
              fullText += `\n\n> 🔍 正在查询${event.name}...\n\n`
              useChatStore.getState().updateLastAssistant(fullText)
              break
            case 'done':
              // Parse render blocks from markdown
              const parsedBlocks = parseRenderBlocks(fullText)
              useChatStore.getState().updateLastAssistant(fullText, parsedBlocks)
              useChatStore.getState().setStreaming(false)
              break
            case 'error':
              fullText += `\n\n❌ ${event.content}`
              useChatStore.getState().updateLastAssistant(fullText)
              useChatStore.getState().setStreaming(false)
              break
          }
        } catch (e) {
          // skip parse errors for partial lines
        }
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      useChatStore.getState().updateLastAssistant(`请求失败: ${err.message}`)
      useChatStore.getState().setStreaming(false)
    }
  })

  return {
    close: () => controller.abort()
  } as EventSource
}

function parseRenderBlocks(text: string): any[] {
  const blocks: any[] = []
  // Parse [ECHARTS: {...}] blocks
  const echartsRegex = /\[ECHARTS:\s*(\{.+?\})\]/gs
  let match
  while ((match = echartsRegex.exec(text)) !== null) {
    try {
      blocks.push({ type: 'echarts', option: JSON.parse(match[1]) })
    } catch {}
  }
  // Parse [TABLE: {...}] blocks
  const tableRegex = /\[TABLE:\s*(\{.+?\})\]/gs
  while ((match = tableRegex.exec(text)) !== null) {
    try {
      blocks.push({ type: 'table', ...JSON.parse(match[1]) })
    } catch {}
  }
  // Parse [FUNDS: {...}] blocks
  const fundsRegex = /\[FUNDS:\s*(\{.+?\})\]/gs
  while ((match = fundsRegex.exec(text)) !== null) {
    try {
      blocks.push({ type: 'fund-cards', ...JSON.parse(match[1]) })
    } catch {}
  }
  return blocks
}
```

---

### Task 8: 前端 Chat 组件树

**Files:**
- Create: `pages/AIChat.tsx`
- Create: `components/chat/ChatPanel.tsx`
- Create: `components/chat/ChatMessages.tsx`
- Create: `components/chat/ChatInput.tsx`
- Create: `components/chat/blocks/TextBlock.tsx`
- Create: `components/chat/blocks/EChartsBlock.tsx`
- Create: `components/chat/blocks/TableBlock.tsx`
- Create: `components/chat/blocks/FundCardsBlock.tsx`

- [ ] **Step 1: 创建 blocks 目录**

```bash
mkdir -p /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-web/src/components/chat/blocks
```

- [ ] **Step 2: 创建 TextBlock**

`components/chat/blocks/TextBlock.tsx`:

```tsx
import ReactMarkdown from 'react-markdown'

export default function TextBlock({ content }: { content: string }) {
  // Remove render block markers from display
  const cleanText = content
    .replace(/\[ECHARTS:\s*\{.+?\}\]/gs, '')
    .replace(/\[TABLE:\s*\{.+?\}\]/gs, '')
    .replace(/\[FUNDS:\s*\{.+?\}\]/gs, '')
    .trim()

  if (!cleanText) return null

  return (
    <div style={{ lineHeight: 1.8 }}>
      <ReactMarkdown>{cleanText}</ReactMarkdown>
    </div>
  )
}
```

- [ ] **Step 3: 创建 EChartsBlock**

`components/chat/blocks/EChartsBlock.tsx`:

```tsx
import ReactECharts from 'echarts-for-react'

export default function EChartsBlock({ option }: { option: any }) {
  if (!option) return null
  return (
    <div style={{ margin: '12px 0', background: '#fff', borderRadius: 8, padding: 8 }}>
      <ReactECharts option={option} style={{ height: 360 }} />
    </div>
  )
}
```

- [ ] **Step 4: 创建 TableBlock**

`components/chat/blocks/TableBlock.tsx`:

```tsx
import { Table } from 'antd'

export default function TableBlock({ columns, dataSource }: { columns: any[]; dataSource: any[] }) {
  if (!columns || !dataSource) return null
  return (
    <div style={{ margin: '12px 0' }}>
      <Table
        columns={columns}
        dataSource={dataSource}
        rowKey={(_, i) => String(i)}
        pagination={false}
        size="small"
        bordered
      />
    </div>
  )
}
```

- [ ] **Step 5: 创建 FundCardsBlock**

`components/chat/blocks/FundCardsBlock.tsx`:

```tsx
import { Card, Row, Col, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import { formatMoney } from '../../../utils/format'

export default function FundCardsBlock({ funds }: { funds: any[] }) {
  const navigate = useNavigate()
  if (!funds || funds.length === 0) return null
  return (
    <Row gutter={[12, 12]} style={{ margin: '12px 0' }}>
      {funds.map((f: any) => (
        <Col span={8} key={f.code}>
          <Card
            size="small"
            hoverable
            title={f.name}
            onClick={() => navigate(`/funds/${f.code}`)}
          >
            <div>净值: {f.nav ? formatMoney(f.nav) : '-'}</div>
            {f.dayIncrease !== undefined && (
              <Tag color={f.dayIncrease >= 0 ? 'red' : 'green'}>
                {(f.dayIncrease * 100).toFixed(2)}%
              </Tag>
            )}
          </Card>
        </Col>
      ))}
    </Row>
  )
}
```

- [ ] **Step 6: 创建 ChatMessages**

`components/chat/ChatMessages.tsx`:

```tsx
import { useEffect, useRef } from 'react'
import { useChatStore } from '../../store/chatStore'
import TextBlock from './blocks/TextBlock'
import EChartsBlock from './blocks/EChartsBlock'
import TableBlock from './blocks/TableBlock'
import FundCardsBlock from './blocks/FundCardsBlock'

export default function ChatMessages() {
  const messages = useChatStore((s) => s.messages)
  const streaming = useChatStore((s) => s.streaming)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streaming])

  if (messages.length === 0) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 120, color: '#bbb' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>📊</div>
        <div style={{ fontSize: 18, color: '#999' }}>有什么我可以帮你的？</div>
        <div style={{ fontSize: 13, marginTop: 8, color: '#ccc' }}>
          试试 "分析我的持仓" 或 "今天市场怎么样"
        </div>
      </div>
    )
  }

  return (
    <div style={{ flex: 1, overflow: 'auto', padding: '16px 24px' }}>
      {messages.map((msg) => (
        <div
          key={msg.id}
          style={{
            display: 'flex',
            marginBottom: 16,
            justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
          }}
        >
          <div
            style={{
              maxWidth: '80%',
              padding: msg.role === 'user' ? '8px 16px' : '4px 0',
              background: msg.role === 'user' ? '#1677ff' : 'transparent',
              color: msg.role === 'user' ? '#fff' : '#333',
              borderRadius: msg.role === 'user' ? 16 : 0,
              fontSize: 14,
            }}
          >
            {msg.role === 'user' ? (
              <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
            ) : (
              <>
                <TextBlock content={msg.content} />
                {msg.blocks?.map((block, i) => {
                  switch (block.type) {
                    case 'echarts': return <EChartsBlock key={i} option={block.option} />
                    case 'table': return <TableBlock key={i} columns={block.columns} dataSource={block.dataSource} />
                    case 'fund-cards': return <FundCardsBlock key={i} funds={block.funds} />
                    default: return null
                  }
                })}
              </>
            )}
          </div>
        </div>
      ))}
      {streaming && (
        <div style={{ color: '#999', fontSize: 13, paddingLeft: 4 }}>
          <span className="typing-dot">●</span>
        </div>
      )}
      <div ref={bottomRef} />
    </div>
  )
}
```

- [ ] **Step 7: 创建 ChatInput**

`components/chat/ChatInput.tsx`:

```tsx
import { useState, useRef } from 'react'
import { Input, Button, Space } from 'antd'
import { SendOutlined } from '@ant-design/icons'
import { sendChatMessage } from '../../api/ai'
import { useChatStore } from '../../store/chatStore'

const quickActions = [
  { label: '今日市场', prompt: '今天A股市场整体表现如何？有哪些重要新闻？' },
  { label: '分析持仓', prompt: '帮我分析我的基金持仓，看看盈亏情况和风险' },
  { label: '热门基金', prompt: '最近哪些基金表现比较好？帮我推荐几只' },
  { label: '指数估值', prompt: '现在主要指数的估值水平怎么样？' },
]

export default function ChatInput() {
  const [value, setValue] = useState('')
  const streaming = useChatStore((s) => s.streaming)
  const conversationId = useChatStore((s) => s.conversationId)
  const isComposing = useRef(false)

  const handleSend = () => {
    const text = value.trim()
    if (!text || streaming) return
    setValue('')
    sendChatMessage(conversationId, text)
  }

  return (
    <div style={{ borderTop: '1px solid #f0f0f0', padding: '12px 24px', background: '#fff' }}>
      <div style={{ marginBottom: 8 }}>
        <Space wrap size={4}>
          {quickActions.map((action) => (
            <Button
              key={action.label}
              size="small"
              type="dashed"
              disabled={streaming}
              onClick={() => sendChatMessage(conversationId, action.prompt)}
            >
              {action.label}
            </Button>
          ))}
        </Space>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <Input.TextArea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onCompositionStart={() => { isComposing.current = true }}
          onCompositionEnd={() => { isComposing.current = false }}
          onPressEnter={(e) => {
            if (!e.shiftKey && !isComposing.current) {
              e.preventDefault()
              handleSend()
            }
          }}
          placeholder="输入你想了解的内容..."
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={streaming}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={streaming}
          style={{ height: 'auto', alignSelf: 'flex-end' }}
        >
          发送
        </Button>
      </div>
    </div>
  )
}
```

- [ ] **Step 8: 创建 ChatPanel（左侧面板）**

`components/chat/ChatPanel.tsx`:

```tsx
import { useEffect } from 'react'
import { Button, List, Typography, Spin } from 'antd'
import { PlusOutlined, DeleteOutlined, FundOutlined, WalletOutlined, LineChartOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import client from '../../api/client'
import { useChatStore } from '../../store/chatStore'
import { sendChatMessage } from '../../api/ai'
import type { ApiResponse } from '../../types'

const { Text } = Typography

interface Conversation {
  id: number
  title: string
  createdAt: string
}

export default function ChatPanel() {
  const queryClient = useQueryClient()
  const conversationId = useChatStore((s) => s.conversationId)
  const setConversationId = useChatStore((s) => s.setConversationId)
  const reset = useChatStore((s) => s.reset)
  const streaming = useChatStore((s) => s.streaming)

  const { data: conversations, isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: async () => {
      const res = await client.get<any, ApiResponse<Conversation[]>>('/ai/conversations')
      return res.data || []
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => client.delete(`/ai/conversations/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['conversations'] }),
  })

  const oneClickActions = [
    { icon: <LineChartOutlined />, label: '今日市场回顾', prompt: '今天A股市场整体表现如何？有哪些重要新闻？行业板块有什么变化？' },
    { icon: <WalletOutlined />, label: '分析我的持仓', prompt: '帮我分析我的基金持仓，看看每只基金的盈亏和风险，给出建议' },
    { icon: <FundOutlined />, label: '行业板块分析', prompt: '现在哪些行业板块表现比较好？资金流向如何？' },
  ]

  return (
    <div style={{ width: 240, borderRight: '1px solid #f0f0f0', display: 'flex', flexDirection: 'column', height: '100%', background: '#fafafa' }}>
      {/* New Chat Button */}
      <div style={{ padding: 12, borderBottom: '1px solid #f0f0f0' }}>
        <Button
          type="primary"
          block
          icon={<PlusOutlined />}
          onClick={reset}
          disabled={streaming}
        >
          新对话
        </Button>
      </div>

      {/* One-click Actions */}
      <div style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>一键分析</Text>
        <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
          {oneClickActions.map((action) => (
            <Button
              key={action.label}
              size="small"
              type="text"
              icon={action.icon}
              disabled={streaming}
              onClick={() => {
                reset()
                setTimeout(() => sendChatMessage(null, action.prompt), 100)
              }}
              style={{ textAlign: 'left', padding: '4px 8px' }}
            >
              {action.label}
            </Button>
          ))}
        </div>
      </div>

      {/* History */}
      <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>
        <Text type="secondary" style={{ fontSize: 12, padding: '0 12px', display: 'block', marginBottom: 4 }}>历史会话</Text>
        {isLoading ? (
          <Spin size="small" style={{ display: 'block', margin: '20px auto' }} />
        ) : (
          <List
            size="small"
            dataSource={conversations || []}
            renderItem={(item: Conversation) => (
              <List.Item
                onClick={() => !streaming && setConversationId(item.id)}
                style={{
                  cursor: 'pointer',
                  padding: '6px 12px',
                  background: conversationId === item.id ? '#e6f4ff' : 'transparent',
                }}
                actions={[
                  <DeleteOutlined
                    key="delete"
                    style={{ color: '#ccc', fontSize: 12 }}
                    onClick={(e) => {
                      e.stopPropagation()
                      deleteMutation.mutate(item.id)
                    }}
                  />
                ]}
              >
                <Text
                  ellipsis
                  style={{
                    fontSize: 13,
                    color: conversationId === item.id ? '#1677ff' : '#666',
                    maxWidth: 160,
                  }}
                >
                  {item.title}
                </Text>
              </List.Item>
            )}
          />
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 9: 创建 AIChat 主页面**

`pages/AIChat.tsx`:

```tsx
import { useEffect } from 'react'
import { Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import ChatPanel from '../components/chat/ChatPanel'
import ChatMessages from '../components/chat/ChatMessages'
import ChatInput from '../components/chat/ChatInput'
import { useChatStore } from '../store/chatStore'
import client from '../api/client'
import type { ApiResponse } from '../types'

export default function AIChat() {
  const conversationId = useChatStore((s) => s.conversationId)
  const addMessage = useChatStore((s) => s.addMessage)
  const setConversationId = useChatStore((s) => s.setConversationId)
  const setLoading = useChatStore((s) => s.setLoading)

  // Load messages when conversation is selected
  const { isLoading } = useQuery({
    queryKey: ['messages', conversationId],
    queryFn: async () => {
      if (!conversationId) return []
      const res = await client.get<any, ApiResponse<any[]>>(`/ai/conversations/${conversationId}`)
      return res.data || []
    },
    enabled: !!conversationId,
    onSuccess: (data: any[]) => {
      // Clear existing and load
      useChatStore.setState({ messages: [] })
      for (const msg of data) {
        addMessage({
          id: msg.id.toString(),
          role: msg.role,
          content: msg.content || '',
        })
      }
      setLoading(false)
    },
  })

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 64px - 48px)', background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
      <ChatPanel />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {isLoading && conversationId ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Spin tip="加载对话历史..." />
          </div>
        ) : (
          <>
            <ChatMessages />
            <ChatInput />
          </>
        )}
      </div>
    </div>
  )
}
```

---

### Task 9: 前端路由 + Layout 改造

**Files:**
- Modify: `App.tsx`
- Modify: `components/Layout.tsx`

- [ ] **Step 1: 修改 App.tsx**

```diff
+ import AIChat from './pages/AIChat'
...
  <Routes>
    <Route path="/" element={<Layout />}>
-     <Route index element={<Navigate to="/dashboard" replace />} />
+     <Route index element={<Navigate to="/chat" replace />} />
+     <Route path="chat" element={<AIChat />} />
      <Route path="dashboard" element={<Dashboard />} />
      ...
    </Route>
  </Routes>
```

- [ ] **Step 2: 修改 Layout.tsx**

要点：
1. 默认首页改为 `/chat`
2. 左侧菜单默认选中对话
3. 菜单添加"AI 对话"项

```diff
const menuItems = [
+ { key: '/chat', icon: <MessageOutlined />, label: 'AI 对话' },
  { key: '/dashboard', icon: <DashboardOutlined />, label: '总览' },
  ...
]
```

注意替换 `selectedKeys` 的逻辑，`Layout.tsx:56` 原来是 `'/' + location.pathname.split('/')[1]`，这已经能正确匹配 `/chat`。

---

### Task 10: 总体验证

- [ ] **Step 1: 后端编译**

```bash
cd /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-server && mvn compile -q
```
Expected: 无输出

- [ ] **Step 2: 前端类型检查**

```bash
cd /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-web && npx tsc --noEmit
```
Expected: 无输出

- [ ] **Step 3: 启动后端验证**

```bash
mvn spring-boot:run -q &
sleep 20
# 测试创建对话
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好"}' 
```
Expected: SSE 流式响应

- [ ] **Step 4: 启动前端验证**

```bash
cd /Users/zxy/刘家辉-毕业设计/论文/test/fund-tracker-web && npx vite --port 3000
```
Expected: 页面正常渲染，对话界面可交互

---

## 实施顺序

1. **Task 1** → 数据模型（无依赖）
2. **Task 2** → Tool 接口 + 注册中心（依赖 Task 1 完成但代码独立）
3. **Task 3** → DeepSeekService 升级（依赖 Task 2）
4. **Task 4** → 12 个工具实现（依赖 Task 2，可并行）
5. **Task 5** → AIChatService（依赖 Task 1, 3, 4）
6. **Task 6** → AIChatController（依赖 Task 5）
7. **Task 7-8** → 前端组件（可并行于后端开发）
8. **Task 9** → 路由 + Layout 改造（依赖 Task 8）
9. **Task 10** → 总体验证
