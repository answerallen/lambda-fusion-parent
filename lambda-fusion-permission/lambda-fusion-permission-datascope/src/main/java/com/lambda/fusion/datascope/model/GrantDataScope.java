package com.lambda.fusion.datascope.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "授权数据权限请求")
public class GrantDataScope {

    @NotBlank(message = "授权主体ID不能为空")
    @Schema(description = "授权主体ID (如角色ID, 用户ID)")
    private String targetId;

    @NotBlank(message = "授权主体类型不能为空")
    @Schema(description = "授权主体类型 (USER, ROLE, ORG, GROUP, CLIENT)")
    private String targetType;

    @NotNull(message = "业务数据类型不能为空")
    @Schema(description = "业务数据类型 (0-部门, 1-项目等)")
    private Integer type;

    @Schema(description = "选中的节点列表")
    private List<DataScopeNodeParam> nodes;

    @Data
    public static class DataScopeNodeParam {
        private String id;
        private Integer checked; // 1-全选 2-半选
        private Integer level;
    }
}
