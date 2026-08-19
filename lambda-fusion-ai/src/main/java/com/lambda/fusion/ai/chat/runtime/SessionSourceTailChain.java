package com.lambda.fusion.ai.chat.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * 按会话键维护的进程内非阻塞源流尾链：决定「什么时候订阅下一条源流」，不包裹源流执行、不占用线程等待。
 * 同一 {@link SessionKey} 的相邻源流按完成信号串行——新节点原子取得并替换当前尾，前驱的排空信号结束后才在
 * 调度器上执行本节点 {@code startAction}；前驱 error、cancel、审计失败均不阻塞后继。这是进程内设施，不持有线程、
 * 数据库或 Workspace 锁，也不替代 AgentScope 对 state、文件和 Sandbox 的内部保护；若未来允许跨节点接续，
 * 需另行设计执行 owner/lease，不能用本尾链充当分布式调度器。
 *
 * @author Jin
 */
@Slf4j
final class SessionSourceTailChain {

    private final ConcurrentMap<SessionKey, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    /**
     * 将一次源流的启动动作挂到会话键的尾链上。
     *
     * @param key 会话键
     * @param scheduler 前驱完成后执行启动动作的调度器
     * @param startAction 真正订阅本次 {@code streamEvents} 的动作
     * @param drained 本次源流的排空信号；其完成即释放后继
     */
    void enqueue(SessionKey key, Executor scheduler, Runnable startAction, CompletionStage<Void> drained) {
        CompletableFuture<Void> node = drained.toCompletableFuture();
        CompletableFuture<Void> predecessor = tails.put(key, node);
        // 无前驱或前驱已完成：立即可启动；否则挂到前驱完成之后。
        if (predecessor == null) {
            scheduler.execute(() -> start(key, startAction));
        } else {
            predecessor.whenCompleteAsync((ignored, error) -> start(key, startAction), scheduler);
        }
        // 本节点完成后，若仍是无后继的当前尾，则摘除该会话键，避免尾链无界增长。
        node.whenComplete((ignored, error) -> tails.remove(key, node));
    }

    private void start(SessionKey key, Runnable startAction) {
        try {
            startAction.run();
        } catch (RuntimeException startFailure) {
            // 启动失败不能阻塞同会话后继：错误各自记录，尾链按完成语义继续。
            log.error("会话源流启动失败: session={}", key.sessionId(), startFailure);
        }
    }
}
