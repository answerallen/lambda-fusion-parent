package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.AiConstants;
import com.lambda.fusion.ai.chat.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatHistory;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.AtomicSessionUpdateService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.suooprt.CostCalculator;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.knowledge.model.VectorSearchResult;
import com.lambda.fusion.ai.knowledge.service.RagService;
import com.lambda.fusion.ai.llm.mapper.LlmModelMapper;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.workflow.service.WorkflowExecutionService;
import com.lambda.fusion.core.utils.AuthUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final LlmModelMapper llmModelMapper;
    private final RagService ragService;
    private final WorkflowExecutionService workflowExecutionService;
    private final AtomicSessionUpdateService atomicSessionUpdateService;
    private final SseEmitterManager sseEmitterManager;
    private final TransactionTemplate transactionTemplate;
    private final CostCalculator costCalculator;

    @Autowired(required = false)
    @Qualifier("agentStreamExecutor")
    private Executor agentParallelExecutor;

    private @NonNull ChatMessageEntity getChatMessageEntity(String sessionId, SendMessage sendMessage) {
        ChatMessageEntity userMsg = new ChatMessageEntity();
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
            resolveStreamExecutor()
                    .execute(() -> doSendMessageStream(sessionId, sendMessage, session, userMsg, clientId));
        } catch (Exception e) {
            log.error("流式消息发送失败", e);
            sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
            throw new AiBusinessException(AiErrorCode.MESSAGE_SEND_FAILED, e);
        }
    }

    private void doSendMessageStream(
            String sessionId,
            SendMessage sendMessage,
            ChatSessionEntity session,
            ChatMessageEntity userMsg,
            String clientId) {
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
                            String aiText = Optional.ofNullable(response.aiMessage())
                                    .map(AiMessage::text)
                                    .orElse("");
                            String finalContent =
                                    fullAnswer.isEmpty() && StrUtil.isNotEmpty(aiText) ? aiText : fullAnswer.toString();
                            ChatMessageEntity aiMsg = new ChatMessageEntity();
                            aiMsg.setSessionId(sessionId);
                            aiMsg.setRole("assistant");
                            aiMsg.setContent(finalContent);
                            aiMsg.setIsRagEnhanced(true);
                            aiMsg.setRetrievedChunks(JSONUtil.toJsonStr(retrievedChunks));

                            applyTokenUsage(response, aiMsg);

                            try {
                                persistStreamMessages(sessionId, userMsg, aiMsg, session.getLlmModelId());
                                sseEmitterManager.sendEvent(clientId, "finish", aiMsg.getId());
                            } catch (Exception e) {
                                log.error("流式消息持久化失败", e);
                                sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("RAG 推理异常", error);
                            sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
                        }
                    });
        } catch (Exception e) {
            log.error("流式消息发送失败", e);
            sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
        }
    }

    private Executor resolveStreamExecutor() {
        return agentParallelExecutor != null ? agentParallelExecutor : Runnable::run;
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
                        String aiText = Optional.ofNullable(response.aiMessage())
                                .map(AiMessage::text)
                                .orElse("");

                        String finalContent = fullAnswer.isEmpty() ? aiText : fullAnswer.toString();
                        ChatMessageEntity messageId =
                                createAssistantMessageEntity(session.getId(), finalContent, false);
                        applyTokenUsage(response, messageId);
                        try {
                            persistStreamMessages(session.getId(), userMsg, messageId, session.getLlmModelId());
                            sseEmitterManager.sendEvent(clientId, "finish", messageId.getId());
                        } catch (Exception e) {
                            log.error("工作流流式消息持久化失败", e);
                            sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("工作流流式执行异常", error);
                        sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
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
        List<ChatMessage> history = buildChatHistory(session.getId(), userMsg.getId());
        history.add(new UserMessage(dto.getContent()));
        request.setMessages(history);
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("question", dto.getContent());
        request.setInputParams(inputParams);
        request.setTraceEnabled(traceEnabled);
        // 聊天层触发：统计结算由 persistStreamMessages 负责，工作流服务跳过结算
        request.setCalledFromChat(true);
        return request;
    }

    private ChatMessageEntity createAssistantMessageEntity(String sessionId, String content, boolean ragEnhanced) {
        ChatMessageEntity chatMessageEntity = new ChatMessageEntity();
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
    public void submitFeedback(String sessionId, String id, Integer feedback) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, "消息标识不能为空");
        }
        ChatMessageEntity entity = this.lambdaQuery()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getId, id)
                .one();
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, "消息不存在: " + id);
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
        validateSessionAccess(session, sessionId);
        return session;
    }

    private void validateSessionAccess(ChatSessionEntity session, String sessionId) {
        String currentTenantId = AuthUtils.getTenantId();
        String currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getName() : null;
        if (StrUtil.isNotBlank(currentTenantId)
                && StrUtil.isNotBlank(session.getTenantId())
                && !currentTenantId.equals(session.getTenantId())) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
        if (StrUtil.isNotBlank(currentUserId)
                && StrUtil.isNotBlank(session.getUserId())
                && !currentUserId.equals(session.getUserId())) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
    }

    private void persistStreamMessages(
            String sessionId, ChatMessageEntity userMsg, ChatMessageEntity aiMsg, String llmModelId) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageMapper.insert(userMsg);
            chatMessageMapper.insert(aiMsg);

            // 计算成本
            BigDecimal cost = calculateMessageCost(aiMsg, llmModelId);

            // 更新会话统计（原子增量，包含成本）
            atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, aiMsg.getTotalTokens(), cost);

            // 原子更新模型统计（并发安全）
            updateModelStatisticsAtomic(llmModelId, aiMsg.getTotalTokens(), cost);

            // [ACCOUNTING] 结构化记账日志
            log.info(
                    "[ACCOUNTING] scene=chat sessionId={} modelId={} tokens={} cost={}",
                    sessionId,
                    llmModelId,
                    aiMsg.getTotalTokens(),
                    cost);
        });
    }

    private BigDecimal calculateMessageCost(ChatMessageEntity aiMsg, String llmModelId) {
        if (llmModelId == null) {
            return BigDecimal.ZERO;
        }

        try {
            var model = llmModelMapper.selectById(llmModelId);
            if (model == null) {
                log.warn("模型不存在，无法计算成本: {}", llmModelId);
                return BigDecimal.ZERO;
            }

            var costResult = costCalculator.calculateCost(
                    aiMsg.getPromptTokens() != null ? aiMsg.getPromptTokens() : 0,
                    aiMsg.getCompletionTokens() != null ? aiMsg.getCompletionTokens() : 0,
                    model.getInputTokenPrice(),
                    model.getOutputTokenPrice());

            return costResult.getTotalCost();
        } catch (Exception e) {
            log.error("计算消息成本失败, modelId={}", llmModelId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 原子增量更新模型统计（并发安全）。
     * <p>直接使用数据库 SET 原子操作，避免先查后改在并发场景下的统计丢失问题。
     * 不抛出异常，失败时仅记录 WARN 日志，保证主流程不受影响。</p>
     */
    private void updateModelStatisticsAtomic(String llmModelId, int tokenCount, BigDecimal cost) {
        if (llmModelId == null) {
            return;
        }
        try {
            BigDecimal safeCost = cost != null ? cost : BigDecimal.ZERO;
            int rows = llmModelMapper.atomicUpdateStatistics(llmModelId, tokenCount, safeCost);
            if (rows == 0) {
                log.warn("[ACCOUNTING] 模型统计更新失败，模型可能不存在: modelId={}", llmModelId);
            } else {
                log.debug("[ACCOUNTING] 模型统计原子更新成功: modelId={} +tokens={} +cost={}", llmModelId, tokenCount, safeCost);
            }
        } catch (Exception e) {
            log.error("[ACCOUNTING] 原子更新模型统计异常, modelId={}", llmModelId, e);
        }
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
     * @param sessionId        会话ID
     * @param excludeMessageId 需要排除的消息ID（可为null）
     * @return 正序排列的历史消息列表（最早在前）
     */
    private List<ChatMessage> buildChatHistory(String sessionId, String excludeMessageId) {
        // Mapper 返回倒序（最新在前），需要反转为正序（最早在前）
        // 查询 DEFAULT_HISTORY_LIMIT + 1 条消息，预留一条用于排除当前消息
        List<ChatMessageEntity> recentMessages =
                chatMessageMapper.listBySessionId(sessionId, AiConstants.DEFAULT_HISTORY_LIMIT + 1);
        List<ChatMessage> history = new ArrayList<>();
        if (recentMessages != null) {
            for (ChatMessageEntity entity : recentMessages) {
                if (excludeMessageId != null && excludeMessageId.equals(entity.getId())) {
                    continue;
                }
                if ("assistant".equals(entity.getRole())) {
                    history.add(new AiMessage(entity.getContent()));
                } else if ("user".equals(entity.getRole()) && entity.getContent() != null) {
                    history.add(new UserMessage(entity.getContent()));
                }
            }
            // 反转为正序：确保历史消息按时间正序排列，LLM 能正确理解对话上下文
            Collections.reverse(history);
        }
        return history;
    }
}
