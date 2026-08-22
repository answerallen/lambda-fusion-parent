package com.lambda.fusion.ai.chat.runtime.event.redis;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.spi.ChatRunEventBackend;
import io.agentscope.core.agui.event.AguiEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.StringCodec;

/**
 * Redis Streams 事件后端：事件落入每 Run 一条 Stream（key 前缀 {@code chat-run:}），供集群任意节点读取。
 * 序号维持「按 Run 全局递增」的单流模型（非按 phase 分 key），与内存后端的写路径 / {@code snapshot_seq} 语义一致；
 * 由 Redis {@code EXPIRE} 承担终态与孤儿流的清理（§6.4），故 {@code compact}/{@code purgeExpired} 为空操作。
 *
 * <p>本后端不做跨 ownership 的严格 fencing / 边界幂等（§9/§12 演进）；订阅侧以「有界 XREAD + DB 复核
 * {@code (status, phaseNo, leaseEpoch)}」兜底边界一致性，变化即触发 RESYNC_REQUIRED。
 *
 * @author Jin
 */
@Slf4j
public final class RedisChatRunEventBackend implements ChatRunEventBackend {

    /** 事件字段名。 */
    private static final String FIELD_DATA = "data";

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_AGUI_RUN_ID = "aguiRunId";

    private final RedissonClient redisson;
    private final String keyPrefix;
    private final int maxEvents;
    private final long maxBytes;
    /** 活动（RUNNING）Run 的 Stream 安全 TTL；写入时续期，防止孤儿流残留。 */
    private final Duration activeTtl;
    /** 每个 Run 的本地下一序号（owner 单点写入， initialize 以 snapshot_seq 对齐）。 */
    private final Map<String, Long> nextSeqByRun = new ConcurrentHashMap<>();

    /**
     * 创建 Redis Streams 事件后端。
     *
     * @param redisson Redis 客户端
     * @param properties AI 模块配置
     */
    public RedisChatRunEventBackend(RedissonClient redisson, AiProperties properties) {
        this.redisson = redisson;
        this.keyPrefix = "chat-run:";
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        // 活动流 TTL 取交互上限与确认上限中的较大者再加余量，保证活跃流不被误清。
        long seconds = Math.max(
                        properties.getChat().getRun().getMaxRunDurationSeconds(),
                        properties.getChat().getRun().getAwaitConfirmTimeoutSeconds())
                + 600;
        this.activeTtl = Duration.ofSeconds(seconds);
    }

    @Override
    public void initialize(String runId, Long latestSeq) {
        long base = latestSeq == null ? 0L : latestSeq;
        nextSeqByRun.merge(runId, base + 1, Math::max);
    }

    @Override
    public boolean appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents) {
        if (aguiEvents == null || aguiEvents.isEmpty()) {
            return false;
        }
        long totalBytes = 0;
        for (AguiEvent event : aguiEvents) {
            long seq = allocateSeq(runId);
            String data = AguiEventJsonCodec.encodeRunEvent(event, runId, aguiRunId, seq);
            totalBytes += data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            add(runId, seq, event.getType().name(), data, aguiRunId);
        }
        return totalBytes > maxBytes;
    }

    @Override
    public boolean runExclusive(String runId, String aguiRunId, List<AguiEvent> aguiEvents, BooleanSupplier dbAction) {
        // 「暂存 → DB 迁移 → 发布」在 Redis 后端简化为「先 DB 事实、后写事件」：DB 成功才 XADD，失败不写，
        // 与内存后端的可见性语义一致（事实先于信号外发）。序号先占后不回收，允许空洞（bootstrap 以 DB 快照为准）。
        boolean committed = dbAction.getAsBoolean();
        if (!committed) {
            return false;
        }
        appendAll(runId, aguiRunId, aguiEvents);
        return true;
    }

    @Override
    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson) {
        // 终态事件以「该类型已存在则复用」做幂等：扫描是否有同类型终态事件。
        List<ChatRunEvent> existing = readAfter(runId, 0);
        String type = AguiEventJsonCodec.readEventType(aguiJson);
        for (ChatRunEvent event : existing) {
            if (event.type() != null && event.type().equals(type)) {
                return event;
            }
        }
        long seq = allocateSeq(runId);
        String data = AguiEventJsonCodec.withRunMetadata(aguiJson, runId, aguiRunId, seq);
        add(runId, seq, type, data, aguiRunId);
        return new ChatRunEvent(seq, runId + ":" + seq, type, data);
    }

    @Override
    public long latestSeq(String runId, Long fallback) {
        // 取最新一条事件的序号作为水位；流不存在或无事件时回退 fallback。
        Map<StreamMessageId, Map<String, String>> last =
                stream(runId).rangeReversed(1, new StreamMessageId(Long.MAX_VALUE, 0), new StreamMessageId(0, 0));
        if (last == null || last.isEmpty()) {
            return fallback == null ? 0L : fallback;
        }
        return last.keySet().iterator().next().getId0();
    }

    @Override
    public void compact(String runId, long snapshotSeq) {
        // 活动流不裁剪（§9 演进项）；容量由 maxEvents/maxBytes 上限在 owner 侧判，清理由 EXPIRE 承担。
    }

    @Override
    public List<ChatRunEvent> readAfter(String runId, long afterSeq) {
        RStream<String, String> stream = stream(runId);
        StreamMessageId from = new StreamMessageId(afterSeq, 0);
        Map<StreamMessageId, Map<String, String>> range = stream.range(from, null);
        List<ChatRunEvent> events = new ArrayList<>();
        if (range == null) {
            return events;
        }
        range.forEach((id, fields) -> {
            long seq = id.getId0();
            if (seq > afterSeq) {
                events.add(new ChatRunEvent(seq, runId + ":" + seq, fields.get(FIELD_TYPE), fields.get(FIELD_DATA)));
            }
        });
        events.sort(java.util.Comparator.comparingLong(ChatRunEvent::seq));
        return events;
    }

    @Override
    public void markTerminal(String runId, Duration retention) {
        stream(runId).expire(retention);
        nextSeqByRun.remove(runId);
    }

    @Override
    public void purgeExpired() {
        // 终态与孤儿流由 Redis EXPIRE 接管，无需本地周期清理。
    }

    @Override
    public void shutdown() {
        nextSeqByRun.clear();
    }

    /** 分配并推进本地下一序号（owner 单点写入；默认从 1 起，initialize 以 snapshot_seq 对齐）。 */
    private long allocateSeq(String runId) {
        // merge 返回推进后的值：首次无记录时放入 1，之后每次 +1。
        return nextSeqByRun.merge(runId, 1L, (current, one) -> current + 1);
    }

    private void add(String runId, long seq, String type, String data, String aguiRunId) {
        RStream<String, String> stream = stream(runId);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_DATA, data);
        fields.put(FIELD_TYPE, type == null ? "" : type);
        fields.put(FIELD_AGUI_RUN_ID, aguiRunId == null ? "" : aguiRunId);
        stream.add(new StreamMessageId(seq, 0), StreamAddArgs.entries(fields));
        // 写入即续期活动 TTL，防止活跃流被 EXPIRE 误清。
        stream.expire(activeTtl);
    }

    private RStream<String, String> stream(String runId) {
        return redisson.getStream(keyPrefix + runId, StringCodec.INSTANCE);
    }
}
