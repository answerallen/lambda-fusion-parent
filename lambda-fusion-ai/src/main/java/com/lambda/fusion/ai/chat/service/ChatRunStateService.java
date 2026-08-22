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
 */
public interface ChatRunStateService {

    /**
     * CAS 认领新建 Run：{@code CREATED -> RUNNING}，同时写入本节点 owner/epoch/lease；已被并发认领则返回 false。
     *
     * @param run 运行实体（携带 id）
     * @param owner 认领节点标识
     * @param leaseUntil 新租约截止时间
     */
    boolean claimCreated(ChatRunEntity run, String owner, java.time.LocalDateTime leaseUntil);

    /**
     * 接管租约已过期的中断态 Run：仅当该 Run 当前 owner/epoch 与预期一致且租约确已过期时，
     * 将 owner/epoch/lease 原子切换为本节点。集群接管（Cycle2a 起）使用；本周期仅提供 CAS 能力，接管调度在后续周期接入。
     *
     * @param run 运行实体（携带 id）
     * @param expectedOwner 预期当前 owner 标识；{@code null} 表示预期无主
     * @param expectedEpoch 预期当前 lease_epoch
     * @param newOwner 接管节点标识
     * @param leaseUntil 新租约截止时间
     * @param dbNow 数据库当前时间（调用方取），用于判定租约确已过期
     * @return 接管成功返回 true；并发落败或租约未过期返回 false
     */
    boolean takeover(
            ChatRunEntity run,
            String expectedOwner,
            Long expectedEpoch,
            String newOwner,
            java.time.LocalDateTime leaseUntil,
            java.time.LocalDateTime dbNow);

    /**
     * 续约本节点持有 Run 的租约：仅当 owner/epoch 仍为本节点且租约尚未过期时续期，过期 owner 不得原地复活。
     *
     * @param run 运行实体（携带 id 与当前 owner/epoch）
     * @param leaseUntil 新租约截止时间
     * @param dbNow 数据库当前时间，用于判定旧租约尚未过期
     * @return 续约成功返回 true；已不是本节点持有或租约已过期返回 false
     */
    boolean renewLease(ChatRunEntity run, java.time.LocalDateTime leaseUntil, java.time.LocalDateTime dbNow);

    boolean checkpoint(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    /**
     * CAS 进入待确认：本节点持有（或尚无主）的 {@code RUNNING -> AWAITING_CONFIRM}，同事务持久化快照并
     * 主动清空 owner/lease（epoch 保留），使待确认运行无主、可被任意节点确认或按确认超时认领收敛。
     *
     * @param run 运行实体（携带 id）
     * @param snapshot 待确认展示快照
     * @param seq 内容事件水位
     * @param deadline 确认截止时间
     * @return 迁移成功返回 true；状态前置不满足或所有权被 fencing 拦截返回 false
     */
    boolean awaitConfirm(ChatRunEntity run, ChatRunSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    /**
     * CAS 确认超时并认领：对已超截止时间的 {@code AWAITING_CONFIRM}（进入时已清空 owner）原子地认领本节点
     * owner/lease、epoch +1 并转 {@code STOPPING}，随后由本节点终结，避免确认超时运行滞留 STOPPING。
     *
     * @param run 运行实体（携带 id）
     * @param deadline 判定已超时的数据库当前时间
     * @param owner 认领节点标识
     * @param leaseUntil 新租约截止时间
     * @return 认领并迁移成功返回 true；未超时或已被并发方处理返回 false
     */
    boolean requestConfirmationTimeout(
            ChatRunEntity run, LocalDateTime deadline, String owner, LocalDateTime leaseUntil);

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

    ChatRunFinalizationResult finalizeExecution(ChatRunEntity run, ChatRunFinalizationCommand command);

    void recordTerminalSeq(ChatRunEntity run, ChatRunSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    /**
     * 查询创建时间早于阈值的 {@code CREATED} 运行：用于「全节点长期满载」时按调度超时收敛，
     * 避免新建运行无限期滞留待认领。
     *
     * @param createdBefore 创建时间阈值；早于该时刻仍未被认领者视为调度超时
     * @return 调度超时的待认领运行
     */
    List<ChatRunEntity> listStaleCreated(LocalDateTime createdBefore);

    /**
     * 查询服务重启或周期扫描后需要接管/终结的中断态运行：仅返回「无主或租约已过期」者，
     * 健康节点仍持有租约的 Run 不在结果中，避免滚动发布误终结。
     *
     * @param expiredBefore 租约过期阈值；{@code lease_until} 早于该时刻或 owner 为 NULL 视为可接管
     * @return 可接管的中断态运行
     */
    List<ChatRunEntity> listInterruptedOnRestart(LocalDateTime expiredBefore);

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);
}
