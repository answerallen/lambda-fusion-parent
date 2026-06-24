package com.lambda.fusion.ai.knowledge.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentEntity;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档VO
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = DocumentEntity.class, isReverse = true)
@Data
@Schema(description = "文档VO")
public class Document extends BaseEntity {

    @Schema(description = "文档ID")
    private String id;

    @Schema(description = "所属知识库ID")
    private String kbId;

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

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "上传时间")
    private LocalDateTime uploadedAt;

    @Schema(description = "上传用户ID")
    private String uploadedBy;

    @Schema(description = "处理完成时间")
    private LocalDateTime processedAt;
}
