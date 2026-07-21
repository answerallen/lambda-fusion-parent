package com.lambda.fusion.ai.chat.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话流式服务：编排会话加载、消息持久化与 Agent 事件流式输出。
 *
 * @author Jin
 */
public interface ChatService {

    SseEmitter streamChat(String sessionId, String content);
}
