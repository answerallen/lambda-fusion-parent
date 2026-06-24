package com.lambda.fusion.ai.chat.controller;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.ai.chat.model.ChatSession;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/chat/sessions")
@Tag(name = "对话会话管理")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    @Operation(summary = "创建会话")
    public ChatSession create(@RequestBody CreateSession createSession) {
        return chatSessionService.createSession(createSession);
    }

    @GetMapping
    @Operation(summary = "查询用户会话列表")
    public List<ChatSession> list() {
        return chatSessionService.listUserSessions(OperatorUtils.getOperator().getName());
    }

    @PostMapping("/{sessionId}/archive")
    @Operation(summary = "归档会话")
    public void archive(@PathVariable String sessionId) {
        chatSessionService.archiveSession(sessionId);
    }
}
