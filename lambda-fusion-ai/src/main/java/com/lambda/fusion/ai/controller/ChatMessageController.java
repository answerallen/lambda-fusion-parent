package com.lambda.fusion.ai.controller;

import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.model.ChatHistory;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/chat/sessions/{sessionId}/messages")
@Tag(name = "对话消息管理")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SseEmitterManager sseEmitterManager;

    @PostMapping
    @Operation(summary = "发送消息")
    public ChatHistory send(@PathVariable Long sessionId, @Valid @RequestBody SendMessage sendMessage) {
        return chatMessageService.sendMessage(sessionId, sendMessage);
    }

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送消息")
    public SseEmitter streamSend(@PathVariable Long sessionId) {
        String clientId = "chat_" + sessionId;
        return sseEmitterManager.createEmitter(clientId);
    }

    @PostMapping(value = "/stream")
    @Operation(summary = "发起流式对话")
    public void startStreamChat(@PathVariable Long sessionId, @Valid @RequestBody SendMessage sendMessage) {
        chatMessageService.sendMessageStream(sessionId, sendMessage);
    }

    @GetMapping
    @Operation(summary = "查询消息列表")
    public List<ChatHistory> list(@PathVariable Long sessionId, @RequestParam(defaultValue = "50") Integer limit) {
        return chatMessageService.listMessages(sessionId, limit);
    }

    @PostMapping("/{messageId}/feedback")
    @Operation(summary = "提交反馈")
    public void feedback(@PathVariable Long sessionId, @PathVariable Long messageId, @RequestParam Integer feedback) {
        chatMessageService.submitFeedback(messageId, feedback);
    }
}
