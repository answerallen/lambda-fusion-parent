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
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.VectorSearchResult;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import com.lambda.fusion.ai.service.ChatMessageService;
import com.lambda.fusion.ai.service.RagService;
import com.lambda.fusion.ai.service.WorkflowExecutionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
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
    private final WorkflowExecutionService workflowExecutionService;
    private final AtomicSessionUpdateService atomicSessionUpdateService;
    private final SseEmitterManager sseEmitterManager;
    private final TransactionTemplate transactionTemplate;

    private @NonNull ChatMessageEntity getChatMessageEntity(String sessionId, SendMessage sendMessage) {
        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setMessageId(IdUtil.fastSimpleUUID());
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(sendMessage.getContent());
        userMsg.setIsRagEnhanced(false);
        return userMsg;
    }

    @Override
    public void sendMessageStream(String sessionId, SendMessage sendMessage) {
        ChatSessionEntity session = getSessionOrThrow(sessionId);
        validateSessionActive(session);

        String clientId = "chat_" + sessionId;

        ChatMessageEntity userMsg = getChatMessageEntity(sessionId, sendMessage);

        try {
            if (session.getWorkflowId() != null) {
                executeWorkflowStream(session, sendMessage, userMsg, clientId);
                return;
            }
            List<VectorSearchResult> retrievedChunks =
                    ragService.retrieve(sendMessage.getContent(), session.getKbId(), null, null);

            StringBuilder fullAnswer = new StringBuilder();
            List<ChatMessage> history = buildChatHistory(sessionId, null);

            ragService.streamChat(
                    sendMessage.getContent(),
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
                            String aiText = response.aiMessage() != null
                                            && response.aiMessage().text() != null
                                    ? response.aiMessage().text()
                                    : null;
                            String finalContent =
                                    fullAnswer.isEmpty() && aiText != null ? aiText : fullAnswer.toString();

                            ChatMessageEntity aiMsg = new ChatMessageEntity();
                            aiMsg.setMessageId(IdUtil.fastSimpleUUID());
                            aiMsg.setSessionId(sessionId);
                            aiMsg.setRole("assistant");
                            aiMsg.setContent(finalContent);
                            aiMsg.setIsRagEnhanced(true);
                            aiMsg.setRetrievedChunks(JSONUtil.toJsonStr(retrievedChunks));

                            applyTokenUsage(response, aiMsg);

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

    private void applyTokenUsage(ChatResponse response, ChatMessageEntity chatMessageEntity) {
        int promptTokens = 0;
        int completionTokens = 0;
        if (response.tokenUsage() != null) {
            promptTokens = response.tokenUsage().inputTokenCount();
            completionTokens = response.tokenUsage().outputTokenCount();
        }
        chatMessageEntity.setPromptTokens(promptTokens);
        chatMessageEntity.setCompletionTokens(completionTokens);
        chatMessageEntity.setTotalTokens(promptTokens + completionTokens);
    }

    private void executeWorkflowStream(
            ChatSessionEntity session, SendMessage dto, ChatMessageEntity userMsg, String clientId) {
        StringBuilder fullAnswer = new StringBuilder();
        workflowExecutionService.executeStream(
                session.getWorkflowId(),
                buildWorkflowExecutionRequest(session, dto, userMsg, true),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        sseEmitterManager.sendEvent(clientId, "message", token);
                        fullAnswer.append(token);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        String aiText = response.aiMessage() != null
                                        && response.aiMessage().text() != null
                                ? response.aiMessage().text()
                                : "";
                        String finalContent = fullAnswer.isEmpty() ? aiText : fullAnswer.toString();
                        ChatMessageEntity messageId =
                                createAssistantMessageEntity(session.getId(), finalContent, false);
                        applyTokenUsage(response, messageId);
                        try {
                            persistStreamMessages(session.getId(), userMsg, messageId);
                            sseEmitterManager.sendEvent(clientId, "finish", messageId.getMessageId());
                        } catch (Exception e) {
                            log.error("工作流流式消息持久化失败", e);
                            sseEmitterManager.sendEvent(clientId, "error", e.getMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("工作流流式执行异常", error);
                        sseEmitterManager.sendEvent(clientId, "error", error.getMessage());
                    }
                });
    }

    private WorkflowExecutionRequest buildWorkflowExecutionRequest(
            ChatSessionEntity session, SendMessage dto, ChatMessageEntity userMsg, boolean traceEnabled) {
        WorkflowExecutionRequest request = new WorkflowExecutionRequest();
        request.setUserId(session.getUserId());
        request.setTenantId(session.getTenantId());
        request.setSessionId(session.getId());
        request.setKbId(session.getKbId());
        request.setLlmModelId(session.getLlmModelId());
        List<ChatMessage> history = buildChatHistory(session.getId(), userMsg.getMessageId());
        history.add(new UserMessage(dto.getContent()));
        request.setMessages(history);
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("question", dto.getContent());
        request.setInputParams(inputParams);
        request.setTraceEnabled(traceEnabled);
        return request;
    }

    private ChatMessageEntity createAssistantMessageEntity(String sessionId, String content, boolean ragEnhanced) {
        ChatMessageEntity chatMessageEntity = new ChatMessageEntity();
        chatMessageEntity.setMessageId(IdUtil.fastSimpleUUID());
        chatMessageEntity.setSessionId(sessionId);
        chatMessageEntity.setRole("assistant");
        chatMessageEntity.setContent(content);
        chatMessageEntity.setIsRagEnhanced(ragEnhanced);
        return chatMessageEntity;
    }

    @Override
    public List<ChatHistory> listMessages(String sessionId, Integer limit) {
        getSessionOrThrow(sessionId);
        return chatMessageMapper.listBySessionId(sessionId, limit).stream()
                .map(this::toChatHistory)
                .collect(Collectors.toList());
    }

    @Override
    public void submitFeedback(String sessionId, String messageId, Integer feedback) {
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

    private ChatHistory toChatHistory(ChatMessageEntity entity) {
        return ConvertUtils.convert(entity);
    }

    private ChatSessionEntity getSessionOrThrow(String sessionId) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
        return session;
    }

    private void persistStreamMessages(String sessionId, ChatMessageEntity userMsg, ChatMessageEntity aiMsg) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageMapper.insert(userMsg);
            chatMessageMapper.insert(aiMsg);
            atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, aiMsg.getTotalTokens());
        });
    }

    private void validateSessionActive(ChatSessionEntity session) {
        if (session == null) {
            return;
        }
        if ("ARCHIVED".equals(session.getStatus())) {
            throw new AiBusinessException(AiErrorCode.SESSION_ARCHIVED);
        }
    }

    /**
     * 构建聊天历史消息列表
     * <p>
     * <strong>顺序说明</strong>：
     * <ol>
     *   <li>Mapper 返回按 created_at DESC 排序的消息（最新在前）</li>
     *   <li>遍历添加到列表后，顺序仍为倒序（最新在前）</li>
     *   <li>调用 {@link java.util.Collections#reverse(List)} 反转为正序（最早在前）</li>
     *   <li>最终返回正序历史，符合对话时间线，供 LLM 正确理解上下文</li>
     * </ol>
     *
     * @param sessionId 会话ID
     * @param excludeMessageId 需要排除的消息ID（可为null）
     * @return 正序排列的历史消息列表（最早在前）
     */
    private List<ChatMessage> buildChatHistory(String sessionId, String excludeMessageId) {
        // Mapper 返回倒序（最新在前），需要反转为正序（最早在前）
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
            // 反转为正序：确保历史消息按时间正序排列，LLM 能正确理解对话上下文
            java.util.Collections.reverse(history);
        }
        return history;
    }
}
