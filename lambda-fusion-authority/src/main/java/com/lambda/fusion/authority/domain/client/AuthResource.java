package com.lambda.fusion.authority.domain.client;

import com.lambda.fusion.core.tree.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限树
 */
@Getter
@Setter
@Schema(description = "资源树节点信息")
public class AuthResource implements TreeNode<AuthResource> {

    @Schema(description = "父节点编号")
    private String id;

    @Schema(description = "资源名称")
    private String name;

    @Schema(description = "资源路径")
    private String url;

    @Schema(description = "父节点编号")
    private String parentId;

    @Schema(description = "是否选中")
    private Boolean checked;

    @Schema(description = "下级子节点信息")
    private List<AuthResource> children;

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String pid() {
        return this.parentId;
    }

    @Override
    public void children(List<AuthResource> children) {
        this.children = children;
    }
}
