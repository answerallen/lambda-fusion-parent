package com.lambda.fusion.ai.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.chat.model.ChatMessageView;
import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ChatSessionPage;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "查询会话当前活跃Run")
    @GetMapping("/{id}/runs/active")
    public ChatRun activeRun(@Parameter(description = "会话ID", required = true) @PathVariable String id) {
        return chatService.activeRun(id).orElse(null);
    }

    @Operation(summary = "查询对话Run详情")
    @GetMapping("/{id}/runs/{runId}")
    public ChatRun run(
            @Parameter(description = "会话ID", required = true) @PathVariable String id,
            @Parameter(description = "Run ID", required = true) @PathVariable String runId) {
        return chatService.getRun(id, runId);
    }

    @Operation(summary = "续看对话Run事件(SSE)")
    @GetMapping(value = "/{id}/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @Parameter(description = "会话ID", required = true) @PathVariable String id,
            @Parameter(description = "Run ID", required = true) @PathVariable String runId,
            @RequestParam(required = false) Long afterSeq,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(defaultValue = "false") boolean bootstrap) {
        return chatService.resume(id, runId, resolveCursor(runId, afterSeq, lastEventId), bootstrap);
    }

    @Operation(summary = "确认指定Run的工具调用(HITL, SSE)")
    @PostMapping(value = "/{id}/runs/{runId}/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirmRun(
            @Parameter(description = "会话ID", required = true) @PathVariable String id,
            @Parameter(description = "Run ID", required = true) @PathVariable String runId,
            @RequestBody @Valid ConfirmToolCall dto) {
        return chatService.confirm(id, runId, dto);
    }

    @OperationLog
    @Operation(summary = "停止指定对话Run")
    @PostMapping("/{id}/runs/{runId}/stop")
    public void stop(
            @Parameter(description = "会话ID", required = true) @PathVariable String id,
            @Parameter(description = "Run ID", required = true) @PathVariable String runId) {
        chatService.stop(id, runId);
    }

    private static long resolveCursor(String runId, Long afterSeq, String lastEventId) {
        if (afterSeq != null) {
            if (afterSeq < 0) {
                throw invalidCursor("afterSeq", null);
            }
            return afterSeq;
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        int separator = lastEventId.lastIndexOf(':');
        if (separator <= 0 || !runId.equals(lastEventId.substring(0, separator))) {
            throw invalidCursor("Last-Event-ID", null);
        }
        try {
            long cursor = Long.parseLong(lastEventId.substring(separator + 1));
            if (cursor < 0) {
                throw new NumberFormatException("negative cursor");
            }
            return cursor;
        } catch (NumberFormatException invalidCursor) {
            throw invalidCursor("Last-Event-ID", invalidCursor);
        }
    }

    private static AiBusinessException invalidCursor(String parameter, Throwable cause) {
        return cause == null
                ? new AiBusinessException(AiErrorCode.INVALID_PARAMETER, parameter)
                : new AiBusinessException(AiErrorCode.INVALID_PARAMETER, cause, parameter);
    }
}
