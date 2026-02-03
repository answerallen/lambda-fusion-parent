package com.lambda.fusion.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 更新知识库DTO
 *
 * @author Jin
 */
@Data
@Schema(description = "更新知识库DTO")
public class UpdateKnowledgeBaseDTO {

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "文本块大小")
    private Integer chunkSize;

    @Schema(description = "块重叠大小")
    private Integer chunkOverlap;

    @Schema(description = "分段策略")
    private String chunkStrategy;

    @Schema(description = "检索Top-K数量")
    private Integer retrievalTopK;

    @Schema(description = "相似度阈值")
    private BigDecimal similarityThreshold;
}
