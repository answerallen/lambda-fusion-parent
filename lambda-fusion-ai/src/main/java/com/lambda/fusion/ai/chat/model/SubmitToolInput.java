package com.lambda.fusion.ai.chat.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * HITL 工具调用输入提交请求：用户对 {@code RequireExternalExecutionEvent} 挂起的交互工具调用
 * （单选、多选、文本）给出回复，前端从 {@code RunFinished(interrupt)} 拿到 {@code toolCallId} 后
 * 回传本 DTO 到 {@code POST /v1/ai/sessions/{id}/runs/{runId}/input} 继续 Agent 执行；
 * {@code value} 置空表示取消本次交互。进程重启后可从持久化挂起状态开始下一阶段。
 *
 * @author Jin
 */
@Data
@Schema(description = "工具调用输入提交(HITL)")
public class SubmitToolInput {

    @Schema(description = "待输入阶段号")
    @NotNull(message = "phaseNo不能为空")
    @Min(value = 1, message = "phaseNo必须大于0")
    private Integer phaseNo;

    @Schema(description = "输入回复列表(对应挂起的交互工具调用)")
    @NotEmpty(message = "输入回复不能为空")
    @Valid
    private List<Input> inputs;

    @Data
    @Schema(description = "单个挂起工具调用的输入回复")
    public static class Input {

        @Schema(description = "工具调用ID(来自 RunFinished interrupt 的 toolCallId)")
        @NotBlank(message = "工具调用ID不能为空")
        private String toolCallId;

        @Schema(description = "用户输入值(单选/文本为字符串,多选为数组;置空表示取消本次交互)")
        private JsonNode value;
    }
}
