package com.lambda.fusion.ai.rag.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_knowledge_document")
@Schema(description = "知识库文档")
public class KnowledgeDocumentEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("tenant_id")
    @Schema(description = "租户ID")
    private String tenantId;

    @TableField("kb_id")
    @Schema(description = "所属知识库ID")
    private String kbId;

    @TableField("file_name")
    @Schema(description = "文件名")
    private String fileName;

    @TableField("file_type")
    @Schema(description = "文件类型(pdf/docx/txt/md 等)")
    private String fileType;

    @TableField("status")
    @Schema(description = "入库状态: PENDING/READY/FAILED")
    private String status;

    @TableField("chunk_count")
    @Schema(description = "切块数")
    private Integer chunkCount;

    @TableField("error_msg")
    @Schema(description = "失败原因")
    private String errorMsg;

    @TableField("storage_type")
    @Schema(description = "原文件存储类型: LOCAL/OSS")
    private String storageType;

    @TableField("storage_path")
    @Schema(description = "原文件存储路径(本地相对路径或 OSS objectKey)")
    private String storagePath;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
