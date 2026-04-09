package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI文档实体类
 *
 * @author Jin
 */
@Data
@TableName("ai_document")
@Schema(description = "文档实体")
public class DocumentEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "文档ID")
    private String id;

    /**
     * 所属知识库ID
     */
    @Schema(description = "所属知识库ID")
    private String kbId;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * 文件类型
     */
    @Schema(description = "文件类型(PDF、DOCX、MD、TXT等)")
    private String fileType;

    /**
     * 文件大小(字节)
     */
    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    /**
     * 文件哈希(SHA-256)
     */
    @Schema(description = "文件哈希(用于去重)")
    private String fileHash;

    // ==================== 存储信息 ====================

    /**
     * 存储类型
     */
    @Schema(description = "存储类型(LOCAL、OSS、S3等)")
    private String storageType;

    /**
     * 存储路径
     */
    @Schema(description = "存储路径")
    private String storagePath;

    /**
     * 访问URL
     */
    @Schema(description = "访问URL")
    private String storageUrl;

    // ==================== 处理信息 ====================

    /**
     * 分段数量
     */
    @Schema(description = "文档分段数量")
    private Integer chunkCount;

    /**
     * 向量数量
     */
    @Schema(description = "向量数量")
    private Integer vectorCount;

    /**
     * 处理状态
     */
    @Schema(description = "处理状态(PENDING、PROCESSING、COMPLETED、FAILED)")
    private String processStatus;

    /**
     * 处理进度(0-100)
     */
    @Schema(description = "处理进度(0-100)")
    private Integer processProgress;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    // ==================== 元数据 ====================

    /**
     * 自定义元数据(JSONB)
     */
    @Schema(description = "自定义元数据(JSON格式)")
    private String metadata;

    // ==================== 统计信息 ====================

    /**
     * 字符数
     */
    @Schema(description = "字符数")
    private Integer charCount;

    /**
     * 词数
     */
    @Schema(description = "词数")
    private Integer wordCount;

    /**
     * 页数
     */
    @Schema(description = "页数")
    private Integer pageCount;

    // ==================== 审计字段 ====================

    /**
     * 租户隔离ID
     */
    @Schema(description = "租户隔离ID")
    private String tenantId;

    /**
     * 上传时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "上传时间")
    private LocalDateTime uploadedAt;

    /**
     * 上传用户ID
     */
    @Schema(description = "上传用户ID")
    private String uploadedBy;

    /**
     * 处理完成时间
     */
    @Schema(description = "处理完成时间")
    private LocalDateTime processedAt;

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
     * 删除时间(软删除)
     */
    @Schema(description = "删除时间")
    private LocalDateTime deletedAt;
}
