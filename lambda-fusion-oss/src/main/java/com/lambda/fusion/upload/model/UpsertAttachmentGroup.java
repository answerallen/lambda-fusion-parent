package com.lambda.fusion.upload.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "新增或更新附件分组")
public class UpsertAttachmentGroup {
    @NotBlank
    @Schema(description = "分组名称")
    private String groupName;

    @Schema(description = "分组编码")
    private String groupCode;

    @Schema(description = "排序号")
    private Integer sortNo = 0;
}
