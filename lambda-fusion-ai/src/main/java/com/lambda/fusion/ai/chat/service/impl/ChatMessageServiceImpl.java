package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.agent.runtime.AgentRuntimeService;
import com.lambda.fusion.ai.agent.runtime.EventToSseAdapter;
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
import com.lambda.fusion.ai.llm.mapper.LlmModelMapper;
import com.lambda.fusion.core.utils.AuthUtils;
import java.math.BigDecimal;
import java.util.List;
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
 * <p>AgentScope 重构后：聊天主路坍缩为单一 {@link AgentRuntimeService#run} -> {@link EventToSseAdapter}
 * -> SSE 路径（旧 RAG {@code streamChat} + workflow {@code executeWorkflowStream} 二分已移除）。RAG 检索
 * 改为 agent 的 {@code retrieve_knowledge} 工具（经 {@code KnowledgeRetrievalTools}，在
 * {@code AgentRuntimeServiceImpl.buildToolkit} 注册），agent 自主决定何时检索；workflow 路径随 workflow
 * 域退出（Phase 3 cutover 删 workflow 域）。本文件已清零 langchain4j 依赖（6 处耦合之一）。
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
    private final AgentRuntimeService agentRuntimeService;
    private final EventToSseAdapter eventToSseAdapter;
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
        String llmModelId = session.getLlmModelId();
        boolean ragEnhanced = session.getKbIds() != null && !session.getKbIds().isEmpty();
        // 单一路径：AgentRuntimeService.run -> Flux<AgentEvent> -> EventToSseAdapter -> SSE
        // token 流（message 事件）由 adapter 内部 doOnNext 推送；error 由 adapter doOnError 推送
        eventToSseAdapter
                .bridge(clientId, agentRuntimeService.run(session, sendMessage))
                .subscribe(
                        outcome -> {
                            ChatMessageEntity aiMsg = new ChatMessageEntity();
                            aiMsg.setSessionId(sessionId);
                            aiMsg.setRole("assistant");
                            aiMsg.setContent(outcome.answer());
                            aiMsg.setIsRagEnhanced(ragEnhanced);
                            aiMsg.setPromptTokens(outcome.inputTokens());
                            aiMsg.setCompletionTokens(outcome.outputTokens());
                            aiMsg.setTotalTokens(outcome.inputTokens() + outcome.outputTokens());
                            try {
                                persistStreamMessages(sessionId, userMsg, aiMsg, llmModelId);
                                sseEmitterManager.sendEvent(clientId, "finish", aiMsg.getId());
                            } catch (Exception e) {
                                log.error("流式消息持久化失败", e);
                                sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
                            }
                        },
                        error -> log.error("AgentScope 聊天流失败, sessionId={}", sessionId, error));
    }

    private Executor resolveStreamExecutor() {
        return agentParallelExecutor != null ? agentParallelExecutor : Runnable::run;
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
}
