package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.ChatMessageEntity;
import com.lambda.fusion.ai.entity.ChatSessionEntity;
import com.lambda.fusion.ai.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.dto.SendMessageDTO;
import com.lambda.fusion.ai.model.dto.VectorSearchResultDTO;
import com.lambda.fusion.ai.model.vo.ChatMessageVO;
import com.lambda.fusion.ai.service.ChatMessageService;
import com.lambda.fusion.ai.service.RagService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendMessage(Long sessionId, SendMessageDTO dto) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setMessageId(IdUtil.fastSimpleUUID());
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(dto.getContent());
        userMsg.setIsRagEnhanced(false);
        chatMessageMapper.insert(userMsg);

        com.lambda.fusion.ai.model.dto.RagResult ragResult = ragService.chat(dto.getContent(), session.getKbId());

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

        session.setLastMessageAt(LocalDateTime.now());
        session.setMessageCount(session.getMessageCount() + 2);
        session.setTotalTokens(session.getTotalTokens() + aiMsg.getTotalTokens());
        chatSessionMapper.updateById(session);

        return entityToVO(aiMsg);
    }

    @Override
    public void sendMessageStream(Long sessionId, SendMessageDTO dto, SseEmitter emitter) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setMessageId(IdUtil.fastSimpleUUID());
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(dto.getContent());
        userMsg.setIsRagEnhanced(false);
        chatMessageMapper.insert(userMsg);

        StringBuilder fullAnswer = new StringBuilder(); // 3. 检索上下文 (预先获取以供流式处理结束时记录)
        List<VectorSearchResultDTO> retrievedChunks = ragService.retrieve(dto.getContent(), session.getKbId(), 5, 0.6);

        // 4. 调用流式 RAG
        ragService.streamChat(dto.getContent(), session.getKbId(), new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                try {
                    emitter.send(SseEmitter.event().data(token));
                    fullAnswer.append(token);
                } catch (Exception e) {
                    log.error("SSE 发送失败", e);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    // 保存最终回复
                    ChatMessageEntity aiMsg = new ChatMessageEntity();
                    aiMsg.setMessageId(IdUtil.fastSimpleUUID());
                    aiMsg.setSessionId(sessionId);
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(fullAnswer.toString());
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

                    // 更新统计
                    session.setLastMessageAt(LocalDateTime.now());
                    session.setMessageCount(session.getMessageCount() + 2);
                    session.setTotalTokens(session.getTotalTokens() + aiMsg.getTotalTokens());
                    chatSessionMapper.updateById(session);

                    emitter.send(SseEmitter.event().name("finish").data(aiMsg.getMessageId()));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("流式响应结算失败", e);
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式响应异常", error);
                try {
                    emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }
        });
    }

    @Override
    public List<ChatMessageVO> listMessages(Long sessionId, Integer limit) {
        return chatMessageMapper.listBySessionId(sessionId, limit).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void submitFeedback(Long messageId, Integer feedback) {
        ChatMessageEntity entity = chatMessageMapper.selectById(messageId);
        entity.setUserFeedback(feedback);
        chatMessageMapper.updateById(entity);
    }

    private ChatMessageVO entityToVO(ChatMessageEntity entity) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
