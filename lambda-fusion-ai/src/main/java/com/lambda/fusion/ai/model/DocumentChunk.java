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
    private Long id;

    @Schema(description = "块唯一标识")
    private String chunkId;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "内容哈希")
    private String contentHash;

    @Schema(description = "块序号")
    private Integer chunkIndex;

    @Schema(description = "页码")
    private Integer pageNumber;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "Token数")
    private Integer tokenCount;

    @Schema(description = "嵌入状态")
    private String embeddingStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
