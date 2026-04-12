package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI知识库实体类
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = KnowledgeBase.class)
@Data
@TableName("ai_knowledge_base")
@Schema(description = "知识库实体")
public class KnowledgeBaseEntity extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "知识库ID")
    private String id;

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称")
    private String name;

    /**
     * 知识库描述
     */
    @Schema(description = "知识库描述")
    private String description;

    /**
     * 分类
     */
    @Schema(description = "分类(技术文档、FAQ、产品手册等)")
    private String category;

    // ==================== 多租户字段 ====================

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    private String tenantId;

    /**
     * 创建者用户ID
     */
    @Schema(description = "创建者用户ID")
    private String ownerUserId;

    // ==================== 向量配置 ====================

    /**
     * Embedding模型
     */
    @Schema(description = "使用的Embedding模型")
    private String embeddingModel;

    /**
     * 向量维度
     */
    @Schema(description = "向量维度(768、1536等)")
    private Integer embeddingDimension;

    /**
     * 向量表名
     */
    @Schema(description = "对应的向量表名(如: ai_vector_store_768)")
    private String vectorTableName;

    // ==================== 文本分段配置 ====================

    /**
     * 文本块大小
     */
    @Schema(description = "文本块大小(tokens)")
    private Integer chunkSize;

    /**
     * 块重叠大小
     */
    @Schema(description = "块重叠大小")
    private Integer chunkOverlap;

    /**
     * 分段策略
     */
    @Schema(description = "分段策略(FIXED、PARAGRAPH、SENTENCE、SLIDING_WINDOW)")
    private String chunkStrategy;

    // ==================== 检索配置 ====================

    /**
     * 检索Top-K数量
     */
    @Schema(description = "检索Top-K数量")
    private Integer retrievalTopK;

    /**
     * 相似度阈值
     */
    @Schema(description = "相似度阈值(0.0-1.0)")
    private BigDecimal similarityThreshold;

    // ==================== 统计信息 ====================

    /**
     * 文档数量
     */
    @Schema(description = "文档数量")
    private Integer documentCount;

    /**
     * 文档块数量
     */
    @Schema(description = "文档块数量")
    private Integer chunkCount;

    /**
     * 向量数量
     */
    @Schema(description = "向量数量")
    private Long vectorCount;

    /**
     * 总大小(字节)
     */
    @Schema(description = "总大小(字节)")
    private Long totalSizeBytes;

    // ==================== 状态字段 ====================

    /**
     * 状态
     */
    @Schema(description = "状态(ACTIVE、ARCHIVED、DELETED)")
    private String status;


    /**
     * 删除时间(软删除)
     */
    @Schema(description = "删除时间(软删除)")
    private LocalDateTime deletedAt;
}
