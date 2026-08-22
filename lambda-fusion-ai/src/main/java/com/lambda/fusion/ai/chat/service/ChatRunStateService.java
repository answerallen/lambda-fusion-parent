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
 * 扫描口，仅限运行时恢复与维护任务；终结契约 {@link ChatRunFinalizationCommand} / {@link ChatRunFinalizationResult}
 * 定义在 {@code chat.model}（ChatRun 域词汇）。
 *
 * <p>降级模型（§0）：单次 ChatRun 绑定一个节点执行，不做运行中调用的跨节点接管。心跳仅用于检测节点失效
 * 并收敛为可重试终态，不构成接管/租约；AgentScope 状态经共享 StateStore 在下一次调用（确认/重试）时恢复。
 */
public interface ChatRunStateService {

    /**
     * CAS 认领新建 Run：{@code CREATED -> RUNNING}，同时标记本节点为执行节点并起跳心跳；已被并发认领则返回 false。
     *
     * @param run 运行实体（携带 id）
     * @return 认领成功返回 true；并发落败返回 false
     */
    boolean claimCreated(ChatRunEntity run);

    /**
     * 更新本节点持有 Run 的执行心跳：仅当该 Run 仍标记由本节点执行且非终态时落库。
     *
     * @param run 运行实体（携带 id）
     * @return 心跳写入成功返回 true；该 Run 已不由本节点执行或已终态返回 false
     */
    boolean heartbeat(ChatRunEntity run);

    boolean checkpoint(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    boolean awaitConfirm(ChatRunEntity run, ChatRunSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline);

    /**
     * 在独立事务内推进确认：复核所有权、做阶段幂等守卫与状态校验，以 {@code (status=AWAITING_CONFIRM, phaseNo)}
     * 为前置条件 CAS 到下一阶段，并标记本节点为执行节点、起跳心跳。确认决策内容由调用方在实例锁内解释，
     * 本方法只负责权威的每-run 临界区迁移。
     *
     * @param identity 运行标识实体（携带 id 与 sessionId）
     * @param expectedSession 调用方已校验归属的会话，用于复核所有权
     * @param sourcePhaseNo 确认来源阶段号
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理过（幂等重放）
     */
    ConfirmTransition advanceConfirmation(ChatRunEntity identity, ChatSessionEntity expectedSession, int sourcePhaseNo);

    ChatRunFinalizationResult finalizeExecution(ChatRunEntity run, ChatRunFinalizationCommand command);

    void recordTerminalSeq(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    /**
     * 查询创建时间早于阈值的 {@code CREATED} 运行：用于「创建后迟迟未被认领」时按调度超时收敛。
     *
     * @param createdBefore 创建时间阈值；早于该时刻仍未被认领者视为调度超时
     * @return 调度超时的待认领运行
     */
    List<ChatRunEntity> listStaleCreated(LocalDateTime createdBefore);

    /**
     * 查询执行节点心跳已超时的中断态运行（RUNNING/STOPPING），供失效收敛标记。
     * 心跳超时仅表示「原执行节点可能已失效」，据此把 Run 收敛为可重试终态，不接管其运行中的调用。
     *
     * @param timedOutBefore 心跳超时阈值；{@code heartbeat_at} 早于该时刻视为节点失效
     * @return 心跳超时的中断态运行
     */
    List<ChatRunEntity> listExpiredHeartbeatRuns(LocalDateTime timedOutBefore);

    /**
     * 查询心跳已超时（或本就无心跳）的中断态运行（RUNNING/STOPPING/AWAITING_CONFIRM），供启动恢复逐个收敛。
     * 仅命中执行节点已失效的 Run；仍存活节点的 Run 心跳新鲜，不在结果中，避免滚动发布误终结。
     *
     * @param timedOutBefore 心跳超时阈值；{@code heartbeat_at} 为空或早于该时刻视为执行节点已失效
     * @return 心跳超时/无心跳的中断态运行
     */
    List<ChatRunEntity> listInterruptedOnRestart(LocalDateTime timedOutBefore);

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);
}
