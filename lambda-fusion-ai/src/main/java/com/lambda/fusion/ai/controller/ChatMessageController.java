package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.dto.SendMessageDTO;
import com.lambda.fusion.ai.model.vo.ChatMessageVO;
import com.lambda.fusion.ai.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/sessions/{sessionId}/messages")
@Tag(name = "对话消息管理")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping
    @Operation(summary = "发送消息")
    public ChatMessageVO send(@PathVariable Long sessionId, @Valid @RequestBody SendMessageDTO dto) {
        return chatMessageService.sendMessage(sessionId, dto);
    }

    @GetMapping
    @Operation(summary = "查询消息列表")
    public List<ChatMessageVO> list(@PathVariable Long sessionId, @RequestParam(defaultValue = "50") Integer limit) {
        return chatMessageService.listMessages(sessionId, limit);
    }

    @PostMapping("/{messageId}/feedback")
    @Operation(summary = "提交反馈")
    public void feedback(@PathVariable Long sessionId, @PathVariable Long messageId, @RequestParam Integer feedback) {
        chatMessageService.submitFeedback(messageId, feedback);
    }
}
