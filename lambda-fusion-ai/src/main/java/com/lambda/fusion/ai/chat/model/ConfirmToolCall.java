package com.lambda.fusion.ai.chat.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * HITL 工具调用确认请求。
 *
 * <p>用户对 {@code RequireUserConfirmEvent} 暂停的工具调用给出确认/拒绝决策，前端从
 * {@code RunFinished(interrupt)} 事件拿到 {@code toolCallId} 后回传本 DTO 到
 * {@code POST /v1/ai/sessions/{id}/runs/{runId}/confirm} 恢复 Agent 执行。
 *
 * @author Jin
 */
@Data
@Schema(description = "工具调用确认(HITL)")
public class ConfirmToolCall {

    @Schema(description = "待确认阶段号")
    @NotNull(message = "phaseNo不能为空")
    @Min(value = 1, message = "phaseNo必须大于0")
    private Integer phaseNo;

    @Schema(description = "确认决策列表(对应 RequireUserConfirmEvent 的待确认工具调用)")
    @NotEmpty(message = "确认决策不能为空")
    @Valid
    private List<Decision> decisions;

    @Data
    @Schema(description = "单个工具调用确认决策")
    public static class Decision {

        @Schema(description = "工具调用ID(来自 RunFinished interrupt 的 toolCallId)")
        @NotBlank(message = "工具调用ID不能为空")
        private String toolCallId;

        @Schema(description = "是否确认执行(true=执行,false=拒绝)")
        private boolean confirmed;
    }
}
