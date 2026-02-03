package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 向量搜索结果DTO
 */
@Data
@Schema(description = "向量搜索结果")
public class VectorSearchResult {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "向量ID")
    private String vectorId;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "元数据")
    private String metadata;

    @Schema(description = "相似度分数(0-1)")
    private Double score;

    @Schema(description = "距离")
    private Double distance;
}
