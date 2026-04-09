package com.lambda.fusion.ai.controller;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.ai.model.ChatSession;
import com.lambda.fusion.ai.model.CreateSession;
import com.lambda.fusion.ai.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 对话会话管理 Controller
 *
 * <p><strong>sessionId 语义说明</strong>:
 * URL 路径中的 {@code {sessionId}} 为会话表主键（即 {@code ChatSessionEntity.id}），
 * 而非表内业务字段 {@code sessionId}。
 * 后续版本计划将路径参数改名为 {@code id} 以统一语义。</p>
 */
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
    public void archive(
            /** 会话主键 id（路径参数名沿用 sessionId，实为主键，后续计划统一改名） */
            @PathVariable String sessionId) {
        chatSessionService.archiveSession(sessionId);
    }
}
