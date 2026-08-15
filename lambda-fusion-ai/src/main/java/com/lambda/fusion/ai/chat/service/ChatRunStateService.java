package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话 Run 的执行器状态机面：仅供 {@code ExecutionCoordinator} 使用，不面向 Controller 链路暴露。
 *
 * <p>与 {@link ChatRunService}（HTTP 编排面）的边界：
 *
 * <ul>
 *   <li>方法不做会话归属校验，入参为已通过所有权校验的领域对象；
 *   <li>状态迁移方法均以 {@code REQUIRES_NEW} 独立提交，使每一次状态推进/快照不被
 *       驱动事务的后续失败回滚——这是 Run 进度可恢复的关键；
 *   <li>迁移以「带前置条件的 UPDATE」（CAS）实现，{@code changed == 1} 才视为迁移成功；
 *   <li>{@code loadCurrent} / {@code list*} 为跨会话扫描口，仅限执行器恢复与维护任务使用；
 *   <li>终结契约 {@link FinalizeCommand} / {@link FinalizeResult} 定义在 {@code chat.execution.model}（执行器域词汇）。
 * </ul>
 */
public interface ChatRunStateService {

    boolean claimCreated(ChatRunEntity run);

    boolean checkpoint(ChatRunEntity run, ExecutionSnapshot snapshot, long seq);

    boolean awaitConfirm(ChatRunEntity run, ExecutionSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline);

    FinalizeResult finalizeExecution(ChatRunEntity run, FinalizeCommand command);

    void recordTerminalSeq(ChatRunEntity run, ExecutionSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    List<ChatRunEntity> listInterruptedOnRestart();

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);
}
