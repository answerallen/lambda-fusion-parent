package com.lambda.fusion.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档VO
 *
 * @author Jin
 */
@Data
@Schema(description = "文档VO")
public class DocumentVO {

    @Schema(description = "文档ID")
    private Long id;

    @Schema(description = "文档唯一标识")
    private String documentId;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "文件哈希")
    private String fileHash;

    @Schema(description = "存储类型")
    private String storageType;

    @Schema(description = "存储路径")
    private String storagePath;

    @Schema(description = "访问URL")
    private String storageUrl;

    @Schema(description = "分段数量")
    private Integer chunkCount;

    @Schema(description = "向量数量")
    private Integer vectorCount;

    @Schema(description = "处理状态")
    private String processStatus;

    @Schema(description = "处理进度(0-100)")
    private Integer processProgress;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "元数据")
    private String metadata;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "词数")
    private Integer wordCount;

    @Schema(description = "页数")
    private Integer pageCount;

    @Schema(description = "上传时间")
    private LocalDateTime uploadedAt;

    @Schema(description = "上传用户ID")
    private Long uploadedBy;

    @Schema(description = "处理完成时间")
    private LocalDateTime processedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
