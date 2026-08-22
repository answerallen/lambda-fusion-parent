package com.lambda.fusion.ai.chat.runtime.event.spi;

import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import io.agentscope.core.agui.event.AguiEvent;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 对话执行事件后端：承载单个 Run 的事件暂存、追加、终态标记与游标查询，与订阅分发分离。
 * 实现分内存（单机）与 Redis Streams（集群）两种，由 {@code chat.run.event-backend} 在启动时唯一选定；
 * 写路径与 {@code snapshot_seq}/checkpoint 语义对两种后端完全一致——后端只负责按运行维护全局递增序号的事件序列。
 *
 * @author Jin
 */
public interface ChatRunEventBackend {

    /**
     * 按已持久化序号初始化运行的下一事件序号。
     *
     * @param runId 运行标识
     * @param latestSeq 已持久化的最新事件序号；{@code null} 按 {@code 0} 处理
     */
    void initialize(String runId, Long latestSeq);

    /**
     * 批量追加同一 Agent 事件映射出的 AG-UI 事件。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents AG-UI 事件列表
     * @return 追加后是否超过容量限制（容量超限由 owner 收敛）
     */
    boolean appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents);

    /**
     * 原子地完成「暂存事件 → 数据库迁移 → 按成败发布或丢弃」：暂存事件在迁移成功前不进入可见窗口，
     * 迁移成功才发布、失败则丢弃；序号在暂存时分配，事实先于信号外发（待确认中断场景）。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents 待暂存的 AG-UI 事件
     * @param dbAction 数据库迁移；返回 {@code true} 表示已提交并应发布，{@code false} 表示并发落败应丢弃
     * @return {@code dbAction} 的结果
     */
    boolean runExclusive(String runId, String aguiRunId, List<AguiEvent> aguiEvents, BooleanSupplier dbAction);

    /**
     * 追加终态事件；终态已存在时返回原事件（幂等）。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiJson 终态事件 JSON
     * @return 新增或已存在的终态事件
     */
    ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson);

    /**
     * 查询运行的最新事件序号。
     *
     * @param runId 运行标识
     * @param fallback 后端无记录时使用的序号
     * @return 最新事件序号
     */
    long latestSeq(String runId, Long fallback);

    /**
     * 收缩已由持久化快照覆盖的超量事件（内存后端物理淘汰；Redis 后端依赖容量上限与 EXPIRE，不裁剪活动流）。
     *
     * @param runId 运行标识
     * @param snapshotSeq 快照覆盖的最大事件序号
     */
    void compact(String runId, long snapshotSeq);

    /**
     * 读取序号大于 {@code afterSeq} 的事件（历史回放与订阅取数）。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @return 序号大于 {@code afterSeq} 的事件列表
     */
    List<ChatRunEvent> readAfter(String runId, long afterSeq);

    /**
     * 标记终态事件的保留时长（Redis 后端据此设 EXPIRE；内存后端据此过期清理）。
     *
     * @param runId 运行标识
     * @param retention 终态事件保留时长
     */
    void markTerminal(String runId, Duration retention);

    /** 清理已过期的终态事件（内存后端周期调用；Redis 后端依赖 EXPIRE，可为空实现）。 */
    void purgeExpired();

    /** 释放后端资源（关闭订阅、线程池等）。 */
    void shutdown();
}
