package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.LlmModel;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LLM模型配置实体类
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = LlmModel.class)
@Data
@TableName("ai_llm_model")
@Schema(description = "LLM模型实体")
public class LlmModelEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "模型ID")
    private String id;

    @Schema(description = "模型名称")
    private String name;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "模型类型(CHAT、EMBEDDING、COMPLETION)")
    private String modelType;

    @Schema(description = "提供商(OPENAI、OLLAMA、AZURE_OPENAI等)")
    private String provider;

    @Schema(description = "API Base URL")
    private String baseUrl;

    @Schema(description = "加密的API Key")
    private String apiKeyEncrypted;

    @Schema(description = "API版本")
    private String apiVersion;

    @Schema(description = "部署名称(Azure)")
    private String deploymentName;

    @Schema(description = "实际模型名")
    private String modelName;

    @Schema(description = "默认温度")
    private BigDecimal defaultTemperature;

    @Schema(description = "默认最大Token数")
    private Integer defaultMaxTokens;

    @Schema(description = "默认Top-P")
    private BigDecimal defaultTopP;

    @Schema(description = "上下文窗口大小")
    private Integer contextWindow;

    @Schema(description = "限流配置(JSON)")
    private String rateLimitConfig;

    @Schema(description = "输入Token单价")
    private BigDecimal inputTokenPrice;

    @Schema(description = "输出Token单价")
    private BigDecimal outputTokenPrice;

    @Schema(description = "能力标签(JSON数组)")
    private String capabilities;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否默认模型")
    private Boolean isDefault;

    @Schema(description = "总调用次数")
    private Long totalCalls;

    @Schema(description = "总Token消耗")
    private Long totalTokens;

    @Schema(description = "总成本")
    private BigDecimal totalCost;

    @Schema(description = "租户ID")
    private String tenantId;
}
