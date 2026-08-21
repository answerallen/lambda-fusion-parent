package com.lambda.fusion.ai.chat.runtime.engine.finalize;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.agui.AgentEventInterpreter;
import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.util.JsonUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * 运行终态落库器：把目标终态、最终快照与终态事件提交到数据库与事件存储。
 * 由执行实例在实例锁内调用；抛出 {@code RuntimeException} 表示提交失败，
 * 由实例按退避策略重试。未提交成功（并发落败）时按持久化事实回读并同步运行实体内存字段。
 *
 * @author Jin
 */
public final class RunFinalizer {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AiProperties properties;

    /**
     * 创建终态落库器。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param properties AI 模块配置
     */
    public RunFinalizer(ChatRunStateService runService, ChatRunEventStore eventStore, AiProperties properties) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.properties = properties;
    }

    /**
     * 提交业务终态：数据库终态迁移、终态事件编码追加、终态序号记录与事件缓冲收缩，
     * 并同步运行实体的内存字段。
     *
     * @param run 运行实体
     * @param snapshot 已闭合的最终执行快照
     * @param interpreter 当前阶段事件解释器（终态事件编码出口）
     * @param status 目标终态
     * @param reason 结束原因
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    public void commitTerminal(
            ChatRunEntity run,
            ExecutionSnapshot snapshot,
            AgentEventInterpreter interpreter,
            ChatRunStatus status,
            ChatRunFinishReason reason,
            ChatRunFailureCode errorCode,
            String errorMessage) {
        long beforeTerminal = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        String toolJson = snapshot.tools().isEmpty()
                ? null
                : JsonUtils.getJsonCodec()
                        .toJson(snapshot.tools().stream()
                                .map(RunFinalizer::toPersistedToolCall)
                                .toList());
        FinalizeResult result = runService.finalizeExecution(
                run, new FinalizeCommand(status, reason, snapshot, toolJson, beforeTerminal, errorCode, errorMessage));
        run.setStatus(result.status());
        run.setFinishReason(result.finishReason());
        run.setErrorCode(result.errorCode());
        run.setErrorMessage(result.errorMessage());
        if (!result.committed()) {
            ChatRunEntity persisted = loadCurrent(run);
            run.setAguiRunId(persisted.getAguiRunId());
            snapshot = ExecutionSnapshotCodec.decode(persisted.getSnapshotJson());
        }
        ChatRunStatus actualStatus = ChatRunStatus.valueOf(run.getStatus());
        AguiEvent terminalEvent = actualStatus == ChatRunStatus.FAILED
                ? new AguiEvent.RunError(
                        run.getSessionId(),
                        run.getAguiRunId(),
                        StringUtils.defaultIfBlank(run.getErrorMessage(), "对话运行失败"),
                        run.getErrorCode())
                : new AguiEvent.RunFinished(
                        run.getSessionId(), run.getAguiRunId(), null, new AguiEvent.RunFinishedSuccessOutcome());
        String json = AguiEventJsonCodec.withTerminalMetadata(
                interpreter.encodeToJson(terminalEvent), actualStatus.name(), run.getFinishReason());
        ChatRunEvent appended = eventStore.appendTerminalIfAbsent(run.getId(), run.getAguiRunId(), json);
        runService.recordTerminalSeq(run, snapshot, appended.seq());
        run.setSnapshotSeq(appended.seq());
        eventStore.compact(run.getId(), appended.seq());
        eventStore.markTerminal(
                run.getId(), Duration.ofSeconds(properties.getChat().getRun().getTerminalTtlSeconds()));
    }

    /** 查询最新持久化运行；不存在时返回传入实体。 */
    private ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    private static Map<String, String> toPersistedToolCall(ExecutionSnapshot.Tool tool) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("toolCallId", tool.toolCallId());
        record.put("toolCallName", tool.toolCallName());
        record.put("args", tool.args());
        record.put("result", tool.result());
        return record;
    }
}
