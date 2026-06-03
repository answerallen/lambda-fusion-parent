package com.lambda.fusion.authority.resource.model;

import com.lambda.fusion.core.tree.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "接口权限树节点")
public class ApiPermissionTreeNode implements TreeNode<ApiPermissionTreeNode> {
    @Schema(description = "节点ID")
    private String id;

    @Schema(description = "父节点ID")
    private String parentId;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "节点类型")
    private String type;

    @Schema(description = "所属应用")
    private String application;

    @Schema(description = "接口分组")
    private String group;

    @Schema(description = "HTTP方法")
    private String method;

    @Schema(description = "接口路径")
    private String path;

    @Schema(description = "接口描述")
    private String description;

    @Schema(description = "权限标识")
    private String permissionId;

    @Schema(description = "是否已绑定")
    private Boolean checked;

    @Schema(description = "子节点")
    private List<ApiPermissionTreeNode> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return parentId;
    }

    @Override
    public void children(List<ApiPermissionTreeNode> children) {
        this.children = children;
    }
}
