package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.dto.CreateSessionDTO;
import com.lambda.fusion.ai.model.vo.ChatSessionVO;
import com.lambda.fusion.ai.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/sessions")
@Tag(name = "对话会话管理")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    @Operation(summary = "创建会话")
    public ChatSessionVO create(@RequestBody CreateSessionDTO dto) {
        return chatSessionService.createSession(dto);
    }

    @GetMapping
    @Operation(summary = "查询用户会话列表")
    public List<ChatSessionVO> list(@RequestParam Long userId) {
        return chatSessionService.listUserSessions(userId);
    }

    @PostMapping("/{sessionId}/archive")
    @Operation(summary = "归档会话")
    public void archive(@PathVariable Long sessionId) {
        chatSessionService.archiveSession(sessionId);
    }
}
