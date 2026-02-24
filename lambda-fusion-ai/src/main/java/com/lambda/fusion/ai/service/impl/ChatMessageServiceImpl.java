package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.ChatMessage;
import com.lambda.fusion.ai.model.RagResult;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.VectorSearchResult;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import com.lambda.fusion.ai.service.ChatMessageService;
import com.lambda.fusion.ai.service.RagService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage sendMessage(Long sessionId, SendMessage dto) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setMessageId(IdUtil.fastSimpleUUID());
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(dto.getContent());
        userMsg.setIsRagEnhanced(false);
        chatMessageMapper.insert(userMsg);

        RagResult ragResult = ragService.chat(dto.getContent(), session.getKbId(), session.getLlmModelId());

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

        return entityToVO(aiMsg);
    }

    @Override
    public void sendMessageStream(Long sessionId, SendMessage dto) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        String clientId = "chat_" + sessionId;

        try {
            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setMessageId(IdUtil.fastSimpleUUID());
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(dto.getContent());
            userMsg.setIsRagEnhanced(false);
            chatMessageMapper.insert(userMsg);

            List<VectorSearchResult> retrievedChunks =
                    ragService.retrieve(dto.getContent(), session.getKbId(), null, null);

            StringBuilder fullAnswer = new StringBuilder();

            ragService.streamChat(
                    dto.getContent(),
                    session.getKbId(),
                    retrievedChunks,
                    session.getLlmModelId(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            sseEmitterManager.sendEvent(clientId, "message", token);
                            fullAnswer.append(token);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            String finalContent = fullAnswer.toString();

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

                            chatMessageMapper.insert(aiMsg);

                            atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, aiMsg.getTotalTokens());

                            sseEmitterManager.sendEvent(clientId, "finish", aiMsg.getMessageId());
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
    public List<ChatMessage> listMessages(Long sessionId, Integer limit) {
        return chatMessageMapper.listBySessionId(sessionId, limit).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void submitFeedback(Long messageId, Integer feedback) {
        // 验证输入参数
        if (messageId == null) {
            throw new AiBusinessException(AiErrorCode.MESSAGE_NOT_FOUND, "消息ID不能为空");
        }

        // 获取实体并在继续之前检查是否为null
        ChatMessageEntity entity = chatMessageMapper.selectById(messageId);
        if (entity == null) {
            throw AiBusinessException.messageNotFound(messageId);
        }

        // 更新反馈 - 此时实体保证非null
        entity.setUserFeedback(feedback);
        chatMessageMapper.updateById(entity);
    }

    private ChatMessage entityToVO(ChatMessageEntity entity) {
        ChatMessage vo = new ChatMessage();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
