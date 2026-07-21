package com.lambda.fusion.ai.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.chat.model.ChatSessionPage;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;

/**
 * 对话会话服务。
 *
 * @author Jin
 */
public interface ChatSessionService {

    Page<ChatSessionEntity> page(ChatSessionPage query);

    ChatSessionEntity create(CreateSession dto);

    ChatSessionEntity get(String id);

    void delete(String id);

    /** 加载当前用户拥有的会话（校验租户 + 用户归属），不存在抛出业务异常。 */
    ChatSessionEntity loadOwned(String id);

    void touchLastMessageAt(String id);
}
