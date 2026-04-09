package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.DocumentChunk;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI文档块实体类
 *
 * @author Jin
 */
@AutoConverter(target = DocumentChunk.class)
@Data
@TableName("ai_document_chunk")
@Schema(description = "文档块实体")
public class DocumentChunkEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "文档块ID")
    private String id;

    /**
     * 所属文档ID
     */
    @Schema(description = "所属文档ID")
    private String documentId;

    /**
     * 所属知识库ID
     */
    @Schema(description = "所属知识库ID")
    private String kbId;

    /**
     * 文本内容
     */
    @Schema(description = "文本内容")
    private String content;

    /**
     * 内容哈希
     */
    @Schema(description = "内容哈希(用于去重)")
    private String contentHash;

    // ==================== 位置信息 ====================

    /**
     * 块序号(从0开始)
     */
    @Schema(description = "块序号")
    private Integer chunkIndex;

    /**
     * 起始位置
     */
    @Schema(description = "起始字符位置")
    private Integer startOffset;

    /**
     * 结束位置
     */
    @Schema(description = "结束字符位置")
    private Integer endOffset;

    /**
     * 页码
     */
    @Schema(description = "所在页码")
    private Integer pageNumber;

    // ==================== 向量化信息 ====================

    /**
     * 关联向量ID
     */
    @Schema(description = "关联向量ID")
    private String vectorId;

    /**
     * 向量化状态
     */
    @Schema(description = "向量化状态(PENDING、PROCESSING、COMPLETED、FAILED)")
    private String embeddingStatus;

    /**
     * 向量维度
     */
    @Schema(description = "向量维度")
    private Integer dimension;

    // ==================== 元数据 ====================

    /**
     * 块级元数据(JSONB)
     */
    @Schema(description = "块级元数据(JSON格式)")
    private String metadata;

    // ==================== 统计信息 ====================

    /**
     * 字符数
     */
    @Schema(description = "字符数")
    private Integer charCount;

    /**
     * Token数
     */
    @Schema(description = "Token数")
    private Integer tokenCount;

    // ==================== 审计字段 ====================

    /**
     * 租户隔离ID
     */
    @Schema(description = "租户隔离ID")
    private String tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * 临时存储向量数据 (不映射到 ai_document_chunk 表)
     */
    @TableField(exist = false)
    private java.util.List<Double> embedding;
}
