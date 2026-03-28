package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.ChatHistory;
import com.lambda.fusion.ai.model.RagResult;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.VectorSearchResult;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import com.lambda.fusion.ai.service.ChatMessageService;
import com.lambda.fusion.ai.service.RagService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 消息服务实现类
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity>
        implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final RagService ragService;
    private final AtomicSessionUpdateService atomicSessionUpdateService;
    private final SseEmitterManager sseEmitterManager;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatHistory sendMessage(Long sessionId, SendMessage dto) {
        ChatSessionEntity session = getSessionOrThrow(sessionId);

        ChatMessageEntity userMsg = getChatMessageEntity(sessionId, dto);
        chatMessageMapper.insert(userMsg);

        List<ChatMessage> history = buildChatHistory(sessionId, userMsg.getMessageId());
        RagResult ragResult = ragService.chat(dto.getContent(), session.getKbId(), session.getLlmModelId(), history);

        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setMessageId(IdUtil.fastSimpleUUID());
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(ragResult.getAnswer());
        aiMsg.setIsRagEnhanced(true);
        aiMsg.setRetrievedChunks(JSONUtil.toJsonStr(ragResult.getRetrievedChunks()));
        aiMsg.setPromptTokens(ragResult.getPromptTokens());
        aiMsg.setCompletionTokens(ragResult.getCompletionTokens());
        aiMsg.setTotalTokens(ragResult.getPromptTokens() + ragResult.getCompletionTokens());
        chatMessageMapper.insert(aiMsg);

        // 使用原子操作更新会话统计
        // 这可以防止并发访问时的竞态条件
        atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, aiMsg.getTotalTokens());

        return ConvertUtils.convert(aiMsg);
    }

    private @NonNull ChatMessageEntity getChatMessageEntity(Long sessionId, SendMessage dto) {
        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setMessageId(IdUtil.fastSimpleUUID());
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(dto.getContent());
        userMsg.setIsRagEnhanced(false);
        return userMsg;
    }

    @Override
    public void sendMessageStream(Long sessionId, SendMessage dto) {
        ChatSessionEntity session = getSessionOrThrow(sessionId);

        String clientId = "chat_" + sessionId;

        try {
            ChatMessageEntity userMsg = getChatMessageEntity(sessionId, dto);
            List<VectorSearchResult> retrievedChunks =
                    ragService.retrieve(dto.getContent(), session.getKbId(), null, null);

            StringBuilder fullAnswer = new StringBuilder();
            List<ChatMessage> history = buildChatHistory(sessionId, null);

            ragService.streamChat(
                    dto.getContent(),
                    session.getKbId(),
                    retrievedChunks,
                    session.getLlmModelId(),
                    history,
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            sseEmitterManager.sendEvent(clientId, "message", token);
                            fullAnswer.append(token);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            String finalContent = fullAnswer.isEmpty() && response.aiMessage() != null
                                    ? response.aiMessage().text()
                                    : fullAnswer.toString();

                            ChatMessageEntity aiMsg = new ChatMessageEntity();
                            aiMsg.setMessageId(IdUtil.fastSimpleUUID());
                            aiMsg.setSessionId(sessionId);
                            aiMsg.setRole("assistant");
                            aiMsg.setContent(finalContent);
                            aiMsg.setIsRagEnhanced(true);
                            aiMsg.setRetrievedChunks(JSONUtil.toJsonStr(retrievedChunks));

                            int promptTokens = response.tokenUsage() != null
                                    ? response.tokenUsage().inputTokenCount()
                                    : 0;
                            int completionTokens = response.tokenUsage() != null
                                    ? response.tokenUsage().outputTokenCount()
                                    : 0;
                            aiMsg.setPromptTokens(promptTokens);
                            aiMsg.setCompletionTokens(completionTokens);
                            aiMsg.setTotalTokens(promptTokens + completionTokens);

                            try {
                                persistStreamMessages(sessionId, userMsg, aiMsg);
                                sseEmitterManager.sendEvent(clientId, "finish", aiMsg.getMessageId());
                            } catch (Exception e) {
                                log.error("流式消息持久化失败", e);
                                sseEmitterManager.sendEvent(clientId, "error", e.getMessage());
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("RAG 推理异常", error);
                            sseEmitterManager.sendEvent(clientId, "error", error.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("流式消息发送失败", e);
            sseEmitterManager.sendEvent(clientId, "error", e.getMessage());
            throw new AiBusinessException(AiErrorCode.MESSAGE_SEND_FAILED, e);
        }
    }

    @Override
    public List<ChatHistory> listMessages(Long sessionId, Integer limit) {
        getSessionOrThrow(sessionId);
        return chatMessageMapper.listBySessionId(sessionId, limit).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void submitFeedback(Long sessionId, String messageId, Integer feedback) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }
        if (messageId == null) {
            throw new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, "消息标识不能为空");
        }
        ChatMessageEntity entity = this.lambdaQuery()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getMessageId, messageId)
                .one();
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, "消息不存在: " + messageId);
        }
        entity.setUserFeedback(feedback);
        chatMessageMapper.updateById(entity);
    }

    private ChatHistory entityToVO(ChatMessageEntity entity) {
        return ConvertUtils.convert(entity);
    }

    private ChatSessionEntity getSessionOrThrow(Long sessionId) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
        return session;
    }

    private void persistStreamMessages(Long sessionId, ChatMessageEntity userMsg, ChatMessageEntity aiMsg) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageMapper.insert(userMsg);
            chatMessageMapper.insert(aiMsg);
            atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, aiMsg.getTotalTokens());
        });
    }

    private List<ChatMessage> buildChatHistory(Long sessionId, String excludeMessageId) {
        List<ChatMessageEntity> recentMessages = chatMessageMapper.listBySessionId(sessionId, 11);
        List<ChatMessage> history = new java.util.ArrayList<>();
        if (recentMessages != null) {
            for (ChatMessageEntity entity : recentMessages) {
                if (excludeMessageId != null && excludeMessageId.equals(entity.getMessageId())) {
                    continue;
                }
                if ("assistant".equals(entity.getRole())) {
                    history.add(new AiMessage(entity.getContent()));
                } else if ("user".equals(entity.getRole()) && entity.getContent() != null) {
                    history.add(new UserMessage(entity.getContent()));
                }
            }
            java.util.Collections.reverse(history);
        }
        return history;
    }
}
