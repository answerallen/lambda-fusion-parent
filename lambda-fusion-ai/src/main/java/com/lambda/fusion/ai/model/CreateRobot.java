package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.FieldMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 创建 AI机器人 DTO
 */
@Data
@Schema(description = "创建AI机器人请求体")
public class CreateRobot {

    @NotBlank(message = "机器人名称不能为空")
    @Schema(description = "机器人名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "机器人头像")
    private String avatar;

    @Schema(description = "职能描述")
    private String description;

    @Schema(description = "机器人分类")
    private String category;

    @Schema(description = "关联长文本提示词")
    private String systemPrompt;

    @Schema(description = "LLM模型ID")
    private String llmModelId;

    @Schema(description = "知识库ID")
    private String kbId;

    @Schema(description = "工作流ID")
    private String workflowId;

    @Schema(description = "开启状态", defaultValue = "true")
    private Boolean enabled = true;

    @Schema(description = "是否全局公开", defaultValue = "false")
    private Boolean isPublic = false;

    // ========== 模型参数配置 ==========

    @Schema(description = "模型温度参数(0.0-2.0)", defaultValue = "0.7")
    private BigDecimal temperature;

    @Schema(description = "最大Token数", defaultValue = "4000")
    private Integer maxTokens;

    // ========== 知识库检索配置 ==========

    @Schema(description = "检索TopK", defaultValue = "5")
    private Integer retrievalTopK;

    @Schema(description = "相似度阈值(0.0-1.0)", defaultValue = "0.7")
    private BigDecimal similarityThreshold;

    @Schema(description = "是否显示引用来源", defaultValue = "true")
    private Boolean showCitation;

    // ========== 对话配置 ==========

    @Schema(description = "开场白/欢迎语")
    private String welcomeMessage;

    @FieldMapping(target = "suggestedQuestions", qualifiedByName = "")
    @Schema(description = "预设问题列表")
    private List<String> suggestedQuestions;

    @Schema(description = "是否启用建议追问", defaultValue = "false")
    private Boolean enableFollowUp;

    // ========== 发布配置 ==========
    @FieldMapping(target = "suggestedQuestions", qualifiedByName = "")
    @Schema(description = "发布渠道列表")
    private List<String> publishChannels;
}
