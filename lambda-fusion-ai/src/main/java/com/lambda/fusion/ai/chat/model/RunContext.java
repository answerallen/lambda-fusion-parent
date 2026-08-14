package com.lambda.fusion.ai.chat.model;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;

/** HTTP 编排面返回的 Run 与会话对，供 Controller 链路继续编排（如启动执行或挂接 SSE）。 */
public record RunContext(ChatRunEntity run, ChatSessionEntity session) {}
