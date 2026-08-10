package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话流式服务：编排会话加载、消息持久化与 Agent 事件流式输出。
 *
 * @author Jin
 */
public interface ChatService {

    SseEmitter streamChat(String sessionId, SendMessage message);

    /**
     * HITL 工具调用确认：用户对 {@code RequireUserConfirmEvent} 暂停的工具调用给出确认/拒绝，
     * 携带 {@code Msg.METADATA_CONFIRM_RESULTS} 恢复 Agent 执行（同会话命中 ASKING 状态）。
     *
     * @param sessionId 会话ID
     * @param dto 确认决策
     * @return 恢复执行的 SSE 流
     */
    SseEmitter streamConfirm(String sessionId, ConfirmToolCall dto);
}
