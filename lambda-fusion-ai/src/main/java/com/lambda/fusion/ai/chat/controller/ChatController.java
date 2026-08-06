package com.lambda.fusion.ai.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.chat.model.ChatMessageView;
import com.lambda.fusion.ai.chat.model.ChatSessionPage;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "对话会话")
@RestController
@RequestMapping("/v1/ai/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatService chatService;

    @Operation(summary = "分页查询当前用户的会话")
    @GetMapping("/page")
    public Page<ChatSessionEntity> page(@Valid ChatSessionPage query) {
        return chatSessionService.page(query);
    }

    @Operation(summary = "查询会话详情")
    @GetMapping("/{id}")
    public ChatSessionEntity get(@Parameter(description = "会话ID", required = true) @PathVariable String id) {
        return chatSessionService.get(id);
    }

    @OperationLog
    @Operation(summary = "创建会话")
    @PostMapping
    public ChatSessionEntity create(@RequestBody @Valid CreateSession dto) {
        return chatSessionService.create(dto);
    }

    @OperationLog
    @Operation(summary = "删除会话")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "会话ID", required = true) @PathVariable String id) {
        chatSessionService.delete(id);
    }

    @Operation(summary = "查询会话消息历史")
    @GetMapping("/{id}/messages")
    public List<ChatMessageView> messages(@Parameter(description = "会话ID", required = true) @PathVariable String id) {
        return chatMessageService.listBySession(id);
    }

    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Parameter(description = "会话ID", required = true) @PathVariable String id,
            @RequestBody @Valid SendMessage dto) {
        return chatService.streamChat(id, dto);
    }
}
