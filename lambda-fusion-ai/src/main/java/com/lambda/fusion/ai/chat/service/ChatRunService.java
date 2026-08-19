package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.RunContext;
import com.lambda.fusion.ai.chat.model.SendMessage;
import java.util.Optional;

/**
 * 对话 Run 的 HTTP 编排面：供 Controller 链路使用，方法内置会话归属校验。执行器侧（无归属校验、
 * {@code REQUIRES_NEW} 独立提交）的状态迁移口见 {@link ChatRunStateService}；两接口由
 * {@code ChatRunServiceImpl} 单实现承载，出入参信封 {@link RunContext} 定义在 {@code chat.model}。
 */
public interface ChatRunService {

    RunContext createOrLoad(String sessionId, SendMessage message);

    Optional<ChatRun> getActiveOwned(String sessionId);

    ChatRun getOwned(String sessionId, String runId);

    RunContext loadOwned(String sessionId, String runId);
}
