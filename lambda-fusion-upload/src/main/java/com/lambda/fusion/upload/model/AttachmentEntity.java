package com.lambda.fusion.upload.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("LA_ATTACHMENTS")
@Schema(description = "附件实体")
public class AttachmentEntity {
    @TableId("ATTACHMENT_ID")
    @Schema(description = "附件编号")
    private String id;

    @TableField("FILE_NAME")
    @Schema(description = "文件名")
    private String fileName;

    @TableField("FILE_SIZE")
    @Schema(description = "文件大小")
    private Long fileSize;

    @TableField("CONTENT_TYPE")
    @Schema(description = "文件类型")
    private String contentType;

    @TableField("OBJECT_KEY")
    @Schema(description = "对象存储KEY")
    private String objectKey;

    @TableField("FILE_URL")
    @Schema(description = "文件访问地址")
    private String fileUrl;

    @TableField("GROUP_ID")
    @Schema(description = "分组编号")
    private String groupId;

    @TableField("CLIENT_NAME")
    @Schema(description = "OSS客户端名称")
    private String clientName;

    @TableField("CREATED_AT")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
