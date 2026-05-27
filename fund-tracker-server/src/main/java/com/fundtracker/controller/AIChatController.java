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
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        aiChatService.deleteConversation(id);
        return ApiResponse.success(null);
    }
}
