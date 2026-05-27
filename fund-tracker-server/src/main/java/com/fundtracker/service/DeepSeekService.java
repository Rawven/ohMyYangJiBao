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

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

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
                .timeout(READ_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("DeepSeek API 返回非 200: {} body={}", response.statusCode(), response.body());
                return "分析服务暂时不可用（状态码: " + response.statusCode() + "）";
            }
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
     * 分析基金持仓，返回 LLM 的分析结果
     */
    public String analyzeHoldings(String fundCode, String fundName, String fundType,
                                  String reportDate, List<com.fundtracker.model.entity.FundHolding> holdings) {
        String holdingText = holdings.stream()
                .map(h -> {
                    String change = h.getChangeRatio() != null
                            ? (h.getChangeRatio().compareTo(java.math.BigDecimal.ZERO) >= 0 ? "+" : "")
                            + h.getChangeRatio() + "%" : "--";
                    return String.format("%s(%s) 占比%.2f%% 涨跌幅%s",
                            h.getStockName(), h.getStockCode(),
                            h.getHoldRatio(), change);
                })
                .collect(java.util.stream.Collectors.joining("\n"));

        String prompt = String.format("""
                你是一个专业的基金分析助手。请分析以下基金的最新持仓数据，给出简洁的分析报告。

                基金名称：%s
                基金类型：%s
                报告日期：%s

                前十大持仓：
                %s

                请从以下几个角度分析（控制在300字以内）：
                1. 持仓集中度：前十大持仓的总占比，是否集中
                2. 行业分布：主要分布在哪些行业
                3. 风险提示：潜在的风险点
                4. 综合评价：一句话总结该基金目前的持仓特点
                """, fundName, fundType, reportDate, holdingText);

        return callDeepSeekWithPrompt(prompt);
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
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            log.error("DeepSeek 流式 API 返回非 200: {} body={}", response.statusCode(), errorBody);
            throw new RuntimeException("DeepSeek API 错误: " + response.statusCode());
        }
        InputStream body = response.body();

        // 解析 SSE 流
        Scanner scanner = new Scanner(body, "UTF-8");
        StringBuilder contentBuffer = new StringBuilder();
        Map<Integer, String> toolNames = new HashMap<>();
        Map<Integer, StringBuilder> toolArgsMap = new HashMap<>();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
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

                // tool_calls 片段（按 index 聚合，支持并行多 tool call）
                if (delta.has("tool_calls")) {
                    for (JsonNode tc : delta.path("tool_calls")) {
                        int idx = tc.has("index") ? tc.path("index").asInt() : 0;
                        JsonNode func = tc.path("function");
                        if (func.has("name") && !func.path("name").isNull()) {
                            toolNames.put(idx, func.path("name").asText());
                        }
                        if (func.has("arguments") && !func.path("arguments").isNull()) {
                            toolArgsMap.computeIfAbsent(idx, k -> new StringBuilder())
                                .append(func.path("arguments").asText());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 SSE 块失败: {}", e.getMessage());
            }
        }

        // 按 index 顺序发出所有 tool_call
        List<Integer> sortedIndices = new ArrayList<>(toolNames.keySet());
        Collections.sort(sortedIndices);
        for (int idx : sortedIndices) {
            String name = toolNames.get(idx);
            StringBuilder args = toolArgsMap.get(idx);
            if (name != null && args != null && args.length() > 0) {
                callback.onToolCall(name, args.toString());
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
