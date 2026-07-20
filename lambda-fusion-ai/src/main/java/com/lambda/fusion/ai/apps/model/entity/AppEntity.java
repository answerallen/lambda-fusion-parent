package com.lambda.fusion.ai.apps.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lambda.cloud.core.annotation.FieldMapping;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI机器人实体封装类
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "ai_robot", autoResultMap = true)
@Schema(description = "AI机器人实体")
public class AppEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @Schema(description = "机器人名称")
    private String name;

    @Schema(description = "机器人头像")
    private String avatar;

    @Schema(description = "机器人职能描述")
    private String description;

    @Schema(description = "机器人分类")
    private String category;

    @Schema(description = "绑定的LLM模型ID")
    private String llmModelId;

    @Schema(description = "系统设定人设与初始提示词")
    private String systemPrompt;

    @Schema(description = "关联的知识库ID列表(JSON数组)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> kbIds;


    @Schema(description = "租户隔离ID")
    private String tenantId;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否系统全局公开")
    private Boolean isPublic;

    // ========== 模型参数配置 ==========

    @Schema(description = "模型温度参数(0.0-2.0)")
    private BigDecimal temperature;

    @Schema(description = "最大Token数")
    private Integer maxTokens;

    // ========== 知识库检索配置 ==========

    @Schema(description = "检索TopK")
    private Integer retrievalTopK;

    @Schema(description = "相似度阈值(0.0-1.0)")
    private BigDecimal similarityThreshold;

    @Schema(description = "是否显示引用来源")
    private Boolean showCitation;

    // ========== 对话配置 ==========

    @Schema(description = "开场白/欢迎语")
    private String welcomeMessage;

    @Schema(description = "预设问题(JSON数组)")
    @FieldMapping(target = "suggestedQuestions", qualifiedByName = "stringToList")
    private String suggestedQuestions;

    @Schema(description = "是否启用建议追问")
    private Boolean enableFollowUp;

    // ========== 发布配置 ==========

    @Schema(description = "发布渠道(JSON数组: web/api/wechat等)")
    @FieldMapping(target = "publishChannels", qualifiedByName = "stringToList")
    private String publishChannels;
}
