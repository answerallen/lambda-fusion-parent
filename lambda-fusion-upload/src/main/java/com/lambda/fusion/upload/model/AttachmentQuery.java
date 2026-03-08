package com.lambda.fusion.upload.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "附件查询参数")
public class AttachmentQuery {
    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "分组编号")
    private String groupId;

    @Schema(description = "OSS客户端名称")
    private String clientName;

    @Schema(description = "是否返回预签名地址")
    private Boolean withPreviewUrl;
}
