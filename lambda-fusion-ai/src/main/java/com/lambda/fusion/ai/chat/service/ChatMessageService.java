package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatMessageView;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.util.List;
import java.util.Optional;

public interface ChatMessageService {

    /** 保存用户消息并返回（含自增主键，供附件回填 message_id）。 */
    ChatMessageEntity saveUserMessage(ChatSessionEntity session, String content);

    /** 保存助手消息并返回（含自增主键）。 */
    ChatMessageEntity saveAssistantMessage(ChatSessionEntity session, String content);

    /**
     * 保存助手消息（含工具调用 JSON，供历史回放）。
     *
     * @param toolCall 工具调用快照 JSON，空或 null 表示无工具调用
     */
    ChatMessageEntity saveAssistantMessage(ChatSessionEntity session, String content, String toolCall);

    /** 查询会话历史消息（含各消息附件），按 id 升序。 */
    List<ChatMessageView> listBySession(String sessionId);

    Optional<ChatMessageEntity> findByIdAndSession(Long messageId, String sessionId);

    /**
     * 删除该会话的全部消息。不做租户隔离校验，应由上层调用方先验证会话所有权。
     */
    void deleteBySession(String sessionId);
}
