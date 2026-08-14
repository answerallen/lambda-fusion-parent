package com.lambda.fusion.ai.chat.model;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;

/** 确认工具调用后的迁移结果：{@code resumed=false} 表示阶段已被处理过（幂等重放）。 */
public record ConfirmTransition(ChatRunEntity run, ChatSessionEntity session, boolean resumed) {}
