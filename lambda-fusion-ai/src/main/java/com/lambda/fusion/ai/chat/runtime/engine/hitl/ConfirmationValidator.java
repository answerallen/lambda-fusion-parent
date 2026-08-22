package com.lambda.fusion.ai.chat.runtime.engine.hitl;

import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * HITL 待确认上下文一致性校验：以「最后一条助手消息中的 ASKING 工具调用块」为共同基准，
 * 校验快照待确认工具、用户决策与 Agent 状态三方一致；判定范围与 AgentScope
 * {@code getPendingToolUseIds} 保持一致，扫描更早消息时遗留块可能造成误判。
 *
 * @author Jin
 */
@Slf4j
public final class ConfirmationValidator {

    private ConfirmationValidator() {}

    /**
     * 校验快照、用户决策与 Agent 状态三方一致，并构造携带确认结果的下一阶段输入消息。
     * 决策与快照校验通过后才读取 Agent 状态（惰性供应），保持「非法决策不触碰 Agent 状态」的求值顺序。
     *
     * @param run 运行实体
     * @param pendingTools 快照中的待确认工具调用
     * @param decisions 用户确认决策
     * @param askingBlocksSupplier Agent 状态中当前待确认工具调用块的惰性供应
     * @return 携带确认结果的下一阶段输入消息
     * @throws AiBusinessException 决策非法或三方工具调用不一致
     */
    public static Msg validateAndBuildMessage(
            ChatRunEntity run,
            List<ChatRunSnapshot.ToolCall> pendingTools,
            List<ConfirmToolCall.Decision> decisions,
            Supplier<List<ToolUseBlock>> askingBlocksSupplier) {
        if (decisions == null || decisions.isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "确认决策不能为空");
        }
        Set<String> decidedIds = new HashSet<>();
        for (ConfirmToolCall.Decision decision : decisions) {
            if (StringUtils.isBlank(decision.getToolCallId()) || !decidedIds.add(decision.getToolCallId())) {
                throw new AiBusinessException(
                        AiErrorCode.INVALID_PARAMETER, "确认决策必须完整且不能重复: " + decision.getToolCallId());
            }
        }
        Set<String> snapshotIds = new HashSet<>();
        for (ChatRunSnapshot.ToolCall tool : pendingTools) {
            if (!snapshotIds.add(tool.toolCallId())) {
                throw contextMismatch(run, "快照待确认工具ID重复: " + tool.toolCallId());
            }
        }
        Map<String, ToolUseBlock> blockById = new LinkedHashMap<>();
        for (ToolUseBlock block : askingBlocksSupplier.get()) {
            if (blockById.put(block.getId(), block) != null) {
                throw contextMismatch(run, "Agent待确认工具ID重复: " + block.getId());
            }
        }
        if (!snapshotIds.equals(decidedIds) || !snapshotIds.equals(blockById.keySet())) {
            log.warn(
                    "确认工具上下文不一致: runId={}, phaseNo={}, snapshotCount={}, decisionCount={}, agentAskingCount={}",
                    run.getId(),
                    run.getPhaseNo(),
                    snapshotIds.size(),
                    decidedIds.size(),
                    blockById.size());
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
        }
        List<ConfirmResult> results = decisions.stream()
                .map(decision -> new ConfirmResult(decision.isConfirmed(), blockById.get(decision.getToolCallId())))
                .toList();
        return UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
    }

    private static AiBusinessException contextMismatch(ChatRunEntity run, String detail) {
        log.warn("确认工具上下文不一致: runId={}, phaseNo={}, detail={}", run.getId(), run.getPhaseNo(), detail);
        return new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
    }
}
