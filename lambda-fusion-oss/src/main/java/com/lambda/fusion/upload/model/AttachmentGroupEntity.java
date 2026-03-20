package com.lambda.fusion.upload.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("LA_ATTACHMENT_GROUPS")
@Schema(description = "附件分组")
public class AttachmentGroupEntity {
    @TableId("GROUP_ID")
    @Schema(description = "分组编号")
    private String id;

    @TableField("GROUP_NAME")
    @Schema(description = "分组名称")
    private String groupName;

    @TableField("GROUP_CODE")
    @Schema(description = "分组编码")
    private String groupCode;

    @TableField("SORT_NO")
    @Schema(description = "排序号")
    private Integer sortNo;

    @TableField("OWNER")
    @Schema(description = "拥有者")
    private String owner;

    @TableField("TENANT_ID")
    @Schema(description = "租户ID")
    private String tenantId;

    @TableField("CREATED_AT")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
