package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档块 VO
 */
@Data
@Schema(description = "文档块基本信息")
public class DocumentChunk {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "所属文档ID")
    private String documentId;

    @Schema(description = "所属知识库ID")
    private String kbId;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "内容哈希")
    private String contentHash;

    @Schema(description = "块序号")
    private Integer chunkIndex;

    @Schema(description = "起始位置")
    private Integer startOffset;

    @Schema(description = "结束位置")
    private Integer endOffset;

    @Schema(description = "页码")
    private Integer pageNumber;

    @Schema(description = "关联向量ID")
    private String vectorId;

    @Schema(description = "嵌入状态")
    private String embeddingStatus;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "元数据")
    private String metadata;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "Token数")
    private Integer tokenCount;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
