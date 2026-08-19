package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话 Run 的执行器状态机面：仅供 {@code ExecutionCoordinator} 使用，不面向 Controller 链路暴露。与
 * {@link ChatRunService}（HTTP 编排面）的边界：方法不做会话归属校验（入参为已过权的领域对象）；状态迁移均以
 * {@code REQUIRES_NEW} 独立提交，单次状态推进/快照不被驱动事务的后续失败回滚（Run 进度可恢复的关键）；迁移以
 * 「带前置条件的 UPDATE」(CAS) 实现，{@code changed == 1} 才视为成功；{@code loadCurrent} / {@code list*} 为跨会话
 * 扫描口，仅限执行器恢复与维护任务；终结契约 {@link FinalizeCommand} / {@link FinalizeResult} 定义在
 * {@code chat.execution.model}（执行器域词汇）。
 */
public interface ChatRunStateService {

    boolean claimCreated(ChatRunEntity run);

    boolean checkpoint(ChatRunEntity run, ExecutionSnapshot snapshot, long seq);

    boolean awaitConfirm(ChatRunEntity run, ExecutionSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline);

    /**
     * 在独立事务内推进确认：复核所有权、做阶段幂等守卫与状态校验，以 {@code (status=AWAITING_CONFIRM, phaseNo)}
     * 为前置条件 CAS 到下一阶段。确认决策内容由调用方在实例锁内解释，本方法只负责权威的每-run 临界区迁移。
     *
     * @param identity 运行标识实体（携带 id 与 sessionId）
     * @param expectedSession 调用方已校验归属的会话，用于复核所有权
     * @param sourcePhaseNo 确认来源阶段号
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理过（幂等重放）
     */
    ConfirmTransition advanceConfirmation(ChatRunEntity identity, ChatSessionEntity expectedSession, int sourcePhaseNo);

    FinalizeResult finalizeExecution(ChatRunEntity run, FinalizeCommand command);

    void recordTerminalSeq(ChatRunEntity run, ExecutionSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    List<ChatRunEntity> listInterruptedOnRestart();

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);
}
