package com.lambda.fusion.ai.apps.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * AI应用返回对象 DTO
 */
@Data
@Schema(description = "AI应用返回信息")
public class App {

    private String id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "应用分类")
    private String category;

    @Schema(description = "LLM模型ID")
    private String llmModelId;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "关联知识库ID列表")
    private List<String> kbIds;

    @Schema(description = "所属租户ID")
    private String tenantId;

    @Schema(description = "开启状态")
    private Boolean enabled;

    @Schema(description = "系统公开状态")
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

    @Schema(description = "RAG模式：STATIC/AGENTIC/HYBRID(默认)")
    private String ragMode;

    @Schema(description = "是否显示引用来源")
    private Boolean showCitation;

    // ========== 对话配置 ==========

    @Schema(description = "开场白/欢迎语")
    private String welcomeMessage;

    @Schema(description = "预设问题列表")
    private List<String> suggestedQuestions;

    @Schema(description = "是否启用建议追问")
    private Boolean enableFollowUp;

    // ========== 发布配置 ==========

    @Schema(description = "发布渠道列表")
    private List<String> publishChannels;

    // ========== Agent 模板扩展配置 ==========

    @Schema(description = "绑定的本地工具ID列表(空=全部@Tool)")
    private List<String> toolIds;

    @Schema(description = "绑定的MCP服务器ID列表")
    private List<String> mcpServerIds;

    @Schema(description = "子agent配置(JSON数组)")
    private String subagentSpec;

    @Schema(description = "Tool Group配置(JSON数组)")
    private String toolGroups;

    @Schema(description = "中间件配置(JSON)")
    private String middlewareConfig;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
