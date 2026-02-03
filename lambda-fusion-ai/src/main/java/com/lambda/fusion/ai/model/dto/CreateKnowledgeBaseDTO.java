package com.lambda.fusion.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建知识库DTO
 *
 * @author Jin
 */
@Data
@Schema(description = "创建知识库DTO")
public class CreateKnowledgeBaseDTO {

    @NotBlank(message = "知识库名称不能为空")
    @Schema(description = "知识库名称", required = true)
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "分类(技术文档、FAQ、产品手册等)")
    private String category;

    @NotNull(message = "租户ID不能为空")
    @Schema(description = "租户ID", required = true)
    private Long tenantId;

    @NotNull(message = "创建者ID不能为空")
    @Schema(description = "创建者ID", required = true)
    private Long ownerUserId;

    @NotBlank(message = "Embedding模型不能为空")
    @Schema(description = "Embedding模型", example = "text-embedding-ada-002", required = true)
    private String embeddingModel;

    @NotNull(message = "向量维度不能为空")
    @Schema(description = "向量维度", example = "1536", required = true)
    private Integer embeddingDimension;

    @Schema(description = "文本块大小", example = "500")
    private Integer chunkSize = 500;

    @Schema(description = "块重叠大小", example = "50")
    private Integer chunkOverlap = 50;

    @Schema(description = "分段策略", example = "FIXED")
    private String chunkStrategy = "FIXED";

    @Schema(description = "检索Top-K数量", example = "5")
    private Integer retrievalTopK = 5;

    @Schema(description = "相似度阈值", example = "0.7")
    private BigDecimal similarityThreshold = BigDecimal.valueOf(0.7);
}
