package com.lambda.fusion.ai.knowledge.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库VO
 *
 * @author Jin
 */
@AutoConverter(target = KnowledgeBaseEntity.class, isReverse = true)
@Data
@Schema(description = "知识库VO")
public class KnowledgeBase {

    @Schema(description = "知识库ID")
    private String id;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "创建者ID")
    private String ownerUserId;

    @Schema(description = "Embedding模型")
    private String embeddingModel;

    @Schema(description = "向量维度")
    private Integer embeddingDimension;

    @Schema(description = "向量表名")
    private String vectorTableName;

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

    @Schema(description = "文档数量")
    private Integer documentCount;

    @Schema(description = "文档块数量")
    private Integer chunkCount;

    @Schema(description = "向量数量")
    private Long vectorCount;

    @Schema(description = "总大小(字节)")
    private Long totalSizeBytes;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人ID")
    private String createdBy;

    @Schema(description = "更新人ID")
    private String updatedBy;
}
