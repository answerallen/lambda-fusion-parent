package com.lambda.fusion.ai.chat.runtime.model;

import io.agentscope.core.event.ConfirmResult;
import java.util.List;

/**
 * 已校验的用户确认结果。
 *
 * @param runId 运行标识
 * @param sourcePhaseNo 确认来源阶段号
 * @param results Agent 确认结果
 * @author Jin
 */
public record PreparedConfirmation(String runId, int sourcePhaseNo, List<ConfirmResult> results) {

    public PreparedConfirmation {
        results = List.copyOf(results == null ? List.of() : results);
    }
}
