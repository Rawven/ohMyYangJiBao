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
        2. 根据数据给出分析建议，可以给出具体的操作参考（持有/加仓/减仓/观望），但要注明分析依据和风险提示
        3. 用专业但易懂的中文回答

        工具使用规范（按场景分类）：

        【市场行情】
        - 用户问市场表现、大盘涨跌 → 调用 get_index_valuation
        - 用户问市场新闻、热点事件 → 调用 get_market_news
        - 用户问行业板块分析（如半导体、新能源、医药等） → 调用 get_industry_analysis 并传入 industry
        - 用户问资金流向 → 调用 get_fund_flow

        【基金查询】
        - 用户搜索基金 → 调用 search_funds（支持关键字/类型/公司/净值/涨跌范围筛选）
        - 用户问单只基金详情 → 调用 get_fund_detail
        - 用户问基金持仓股票 → 调用 get_fund_holdings
        - 用户问基金经理 → 调用 get_fund_manager
        - 用户问基金费率 → 调用 get_fund_fees
        - 用户问历史净值 → 调用 get_nav_history（可指定天数）
        - 用户问阶段收益（近1月/3月/6月/1年/3年） → 调用 get_fund_performance
        - 用户问风险指标（最大回撤、波动率等） → 调用 get_fund_risk_metrics
        - 用户对比多只基金 → 调用 compare_funds
        - 用户问热门排行、表现好的基金 → 调用 get_top_funds
        - 用户问基金排行（按类型/涨跌幅排序） → 调用 get_fund_rankings

        【用户持仓】
        - 用户问我的持仓 → 调用 get_portfolio
        - 用户问持仓盈亏 → 调用 get_portfolio_summary 或 analyze_profit
        - 用户问交易记录 → 调用 get_transactions
        - 用户问持仓风险评估 → 调用 analyze_portfolio_risk
        - 用户问定投模拟 → 调用 simulate_drip

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

    @Transactional
    public void deleteConversation(Long id) {
        var msgWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message>();
        msgWrapper.eq(Message::getConversationId, id);
        messageMapper.delete(msgWrapper);
        conversationMapper.deleteById(id);
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

            // 3. 构建消息历史（跳过 tool 消息，因为缺少 tool_call_id 会导致 DeepSeek API 拒绝）
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            for (Message msg : getMessages(conversationId)) {
                if ("tool".equals(msg.getRole())) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                // 如果有持久化的 tool_calls，恢复它们供 DeepSeek 理解上下文
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    try {
                        List<Map<String, Object>> tcs = objectMapper.readValue(msg.getToolCalls(), List.class);
                        m.put("tool_calls", tcs);
                    } catch (Exception e) {
                        log.warn("解析 toolCalls 失败: {}", e.getMessage());
                    }
                }
                messages.add(m);
            }

            // 4. 循环：DeepSeek 推理 -> 可能调工具 -> 再推理
            int maxRounds = 5;
            for (int round = 0; round < maxRounds; round++) {
                StringBuilder fullContent = new StringBuilder();
                List<Map<String, Object>> toolCallsCollected = new ArrayList<>();

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

                // 构造 tool_calls 数组（无论是否保存到 DB，先构造）
                List<Map<String, Object>> allToolCalls = new ArrayList<>();
                List<String> callIds = new ArrayList<>();
                if (hasToolCall[0]) {
                    for (int i = 0; i < toolCallsCollected.size(); i++) {
                        String callId = "call_" + round + "_" + i;
                        callIds.add(callId);
                        allToolCalls.add(Map.of(
                            "id", callId,
                            "type", "function",
                            "function", Map.of(
                                "name", toolCallsCollected.get(i).get("name"),
                                "arguments", toolCallsCollected.get(i).get("arguments")
                            )
                        ));
                    }
                }

                // 保存 assistant 消息到 DB（含 tool_calls JSON 便于后续恢复上下文）
                Message assistantMsg = new Message();
                assistantMsg.setConversationId(conversationId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(fullContent.toString());
                if (!allToolCalls.isEmpty()) {
                    try {
                        assistantMsg.setToolCalls(objectMapper.writeValueAsString(allToolCalls));
                    } catch (Exception e) {
                        log.warn("序列化 toolCalls 失败: {}", e.getMessage());
                    }
                }
                assistantMsg.setCreatedAt(LocalDateTime.now());
                messageMapper.insert(assistantMsg);

                if (!hasToolCall[0]) {
                    break;
                }

                // 将 tool_calls 加入消息历史，供下一轮 DeepSeek 调用
                Map<String, Object> toolCallMsg = new HashMap<>();
                toolCallMsg.put("role", "assistant");
                toolCallMsg.put("content", null);
                toolCallMsg.put("tool_calls", allToolCalls);
                messages.add(toolCallMsg);

                // 执行工具调用，逐个添加 tool 结果
                for (int i = 0; i < toolCallsCollected.size(); i++) {
                    String toolName = (String) toolCallsCollected.get(i).get("name");
                    String argsJson = (String) toolCallsCollected.get(i).get("arguments");

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

                    // 保存 tool 消息到 DB
                    Message toolMsg = new Message();
                    toolMsg.setConversationId(conversationId);
                    toolMsg.setRole("tool");
                    toolMsg.setContent(resultJson);
                    toolMsg.setCreatedAt(LocalDateTime.now());
                    messageMapper.insert(toolMsg);

                    // 加入消息历史，tool_call_id 与 assistant 消息中的 tool_call.id 对应
                    messages.add(Map.of("role", "tool",
                        "content", resultJson,
                        "tool_call_id", callIds.get(i)));
                }

                // 更新会话时间
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
