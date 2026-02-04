package com.lambda.fusion.ai.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * AtomicSessionUpdateServiceImpl的单元测试
 * 测试原子会话统计更新功能
 */
@ExtendWith(MockitoExtension.class)
class AtomicSessionUpdateServiceImplTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @InjectMocks
    private AtomicSessionUpdateServiceImpl atomicSessionUpdateService;

    private Long sessionId;
    private ChatSessionEntity mockSession;

    @BeforeEach
    void setUp() {
        sessionId = 1L;
        mockSession = new ChatSessionEntity();
        mockSession.setId(sessionId);
        mockSession.setVersion(1L);
        mockSession.setMessageCount(5);
        mockSession.setTotalTokens(1000);
        mockSession.setLastMessageAt(LocalDateTime.now().minusMinutes(10));
    }

    @Test
    void updateSessionStatistics_Success() {
        // 给定
        when(chatSessionMapper.atomicUpdateStatistics(eq(sessionId), eq(2), eq(500), any(LocalDateTime.class)))
                .thenReturn(1);

        // 当
        assertDoesNotThrow(() -> atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, 500));

        // 那么
        verify(chatSessionMapper).atomicUpdateStatistics(eq(sessionId), eq(2), eq(500), any(LocalDateTime.class));
    }

    @Test
    void updateSessionStatistics_SessionNotFound() {
        // 给定
        when(chatSessionMapper.atomicUpdateStatistics(eq(sessionId), eq(2), eq(500), any(LocalDateTime.class)))
                .thenReturn(0);

        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class, () -> atomicSessionUpdateService.updateSessionStatistics(sessionId, 2, 500));

        assertEquals(AiErrorCode.SESSION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void updateSessionStatistics_NullSessionId() {
        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class, () -> atomicSessionUpdateService.updateSessionStatistics(null, 2, 500));

        assertEquals(AiErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
        assertEquals("参数无效", exception.getMessage());
    }

    @Test
    void updateSessionStatisticsOptimistic_Success() {
        // 给定
        when(chatSessionMapper.selectByIdWithVersion(sessionId)).thenReturn(mockSession);
        when(chatSessionMapper.updateByIdWithVersion(any(ChatSessionEntity.class)))
                .thenReturn(1);

        // 当
        assertDoesNotThrow(() -> atomicSessionUpdateService.updateSessionStatisticsOptimistic(sessionId, 2, 500));

        // 那么
        verify(chatSessionMapper).selectByIdWithVersion(sessionId);
        verify(chatSessionMapper).updateByIdWithVersion(any(ChatSessionEntity.class));
    }

    @Test
    void updateSessionStatisticsOptimistic_SessionNotFound() {
        // 给定
        when(chatSessionMapper.selectByIdWithVersion(sessionId)).thenReturn(null);

        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class,
                () -> atomicSessionUpdateService.updateSessionStatisticsOptimistic(sessionId, 2, 500));

        assertEquals(AiErrorCode.SESSION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void updateSessionStatisticsOptimistic_OptimisticLockFailure() {
        // 给定
        when(chatSessionMapper.selectByIdWithVersion(sessionId))
                .thenReturn(mockSession)
                .thenReturn(mockSession)
                .thenReturn(mockSession);
        when(chatSessionMapper.updateByIdWithVersion(any(ChatSessionEntity.class)))
                .thenReturn(0) // 第一次尝试失败
                .thenReturn(0) // 第二次尝试失败
                .thenReturn(0); // 第三次尝试失败

        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class,
                () -> atomicSessionUpdateService.updateSessionStatisticsOptimistic(sessionId, 2, 500));

        assertEquals(AiErrorCode.CONCURRENT_UPDATE_FAILED.getCode(), exception.getCode());
        verify(chatSessionMapper, times(3)).selectByIdWithVersion(sessionId);
        verify(chatSessionMapper, times(3)).updateByIdWithVersion(any(ChatSessionEntity.class));
    }

    @Test
    void updateSessionStatisticsOptimistic_OptimisticLockException() {
        // 给定
        when(chatSessionMapper.selectByIdWithVersion(sessionId))
                .thenReturn(mockSession)
                .thenReturn(mockSession)
                .thenReturn(mockSession);
        when(chatSessionMapper.updateByIdWithVersion(any(ChatSessionEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("Version conflict"))
                .thenThrow(new OptimisticLockingFailureException("Version conflict"))
                .thenThrow(new OptimisticLockingFailureException("Version conflict"));

        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class,
                () -> atomicSessionUpdateService.updateSessionStatisticsOptimistic(sessionId, 2, 500));

        assertEquals(AiErrorCode.CONCURRENT_UPDATE_FAILED.getCode(), exception.getCode());
    }

    @Test
    void updateLastMessageTime_Success() {
        // 给定
        LocalDateTime timestamp = LocalDateTime.now();
        when(chatSessionMapper.updateLastMessageTime(sessionId, timestamp)).thenReturn(1);

        // 当
        assertDoesNotThrow(() -> atomicSessionUpdateService.updateLastMessageTime(sessionId, timestamp));

        // 那么
        verify(chatSessionMapper).updateLastMessageTime(sessionId, timestamp);
    }

    @Test
    void updateLastMessageTime_SessionNotFound() {
        // 给定
        LocalDateTime timestamp = LocalDateTime.now();
        when(chatSessionMapper.updateLastMessageTime(sessionId, timestamp)).thenReturn(0);

        // 当 & 那么
        AiBusinessException exception = assertThrows(
                AiBusinessException.class,
                () -> atomicSessionUpdateService.updateLastMessageTime(sessionId, timestamp));

        assertEquals(AiErrorCode.SESSION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void updateLastMessageTime_NullTimestamp() {
        // 给定
        when(chatSessionMapper.updateLastMessageTime(eq(sessionId), any(LocalDateTime.class)))
                .thenReturn(1);

        // 当
        assertDoesNotThrow(() -> atomicSessionUpdateService.updateLastMessageTime(sessionId, null));

        // 那么
        verify(chatSessionMapper).updateLastMessageTime(eq(sessionId), any(LocalDateTime.class));
    }
}
