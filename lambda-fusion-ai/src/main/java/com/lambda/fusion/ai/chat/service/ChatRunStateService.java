package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatRunFinalizationCommand;
import com.lambda.fusion.ai.chat.model.ChatRunFinalizationResult;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话 Run 的运行时状态机面：仅供 {@code ChatRunCoordinator} 使用，不面向 Controller 链路暴露。与
 * {@link ChatRunService}（HTTP 编排面）的边界：方法不做会话归属校验（入参为已过权的领域对象）；状态迁移均以
 * {@code REQUIRES_NEW} 独立提交，单次状态推进/快照不被驱动事务的后续失败回滚（Run 进度可恢复的关键）；迁移以
 * 「带前置条件的 UPDATE」(CAS) 实现，{@code changed == 1} 才视为成功；{@code loadCurrent} / {@code list*} 为跨会话
 * 扫描口，仅限运行时维护任务；终结契约 {@link ChatRunFinalizationCommand} / {@link ChatRunFinalizationResult}
 * 定义在 {@code chat.model}（ChatRun 域词汇）。
 *
 * <p>ChatRun 是 AgentScope 之上的业务状态，不解释执行节点、租约、接管或故障转移；AgentScope 负责底层执行与状态。
 */
public interface ChatRunStateService {

    /**
     * CAS 认领新建 Run：{@code CREATED -> RUNNING}；已被并发认领则返回 false。
     *
     * @param run 运行实体（携带 id）
     * @return 认领成功返回 true；并发落败返回 false
     */
    boolean claimCreated(ChatRunEntity run);

    boolean checkpoint(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    boolean awaitConfirm(ChatRunEntity run, ChatRunSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline);

    /**
     * 在独立事务内推进确认：复核会话归属、做阶段幂等守卫与状态校验，以 {@code (status=AWAITING_CONFIRM, phaseNo)}
     * 为前置条件 CAS 到下一阶段。确认决策内容由调用方在实例锁内解释，本方法只负责权威的每-run 临界区迁移。
     *
     * @param identity 运行标识实体（携带 id 与 sessionId）
     * @param expectedSession 调用方已校验归属的会话，用于复核用户归属
     * @param sourcePhaseNo 确认来源阶段号
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理过（幂等重放）
     */
    ConfirmTransition advanceConfirmation(ChatRunEntity identity, ChatSessionEntity expectedSession, int sourcePhaseNo);

    ChatRunFinalizationResult finalizeExecution(ChatRunEntity run, ChatRunFinalizationCommand command);

    void recordTerminalSeq(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);
}
