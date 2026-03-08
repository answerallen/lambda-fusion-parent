package com.lambda.fusion.upload.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "附件视图")
public class AttachmentView {
    @Schema(description = "附件编号")
    private String id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "文件类型")
    private String contentType;

    @Schema(description = "对象存储KEY")
    private String objectKey;

    @Schema(description = "文件访问地址")
    private String fileUrl;

    @Schema(description = "分组编号")
    private String groupId;

    @Schema(description = "分组名称")
    private String groupName;

    @Schema(description = "OSS客户端名称")
    private String clientName;

    @Schema(description = "预签名访问地址")
    private String previewUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
