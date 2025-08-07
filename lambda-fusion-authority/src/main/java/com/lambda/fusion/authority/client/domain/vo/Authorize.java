package com.lambda.fusion.authority.client.domain.vo;

import com.lambda.fusion.core.tree.Tree;
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
public class Authorize implements Tree<Authorize> {

    @Schema(required = true, description = "父节点编号")
    private String id;

    @Schema(required = true, description = "资源名称")
    private String name;

    @Schema(required = false, description = "资源路径")
    private String url;

    @Schema(required = true, description = "父节点编号")
    private String parentId;

    @Schema(description = "是否选中")
    private Boolean checked;

    @Schema(description = "下级子节点信息")
    private List<Authorize> children;

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String pid() {
        return this.parentId;
    }

    @Override
    public void children(List<Authorize> children) {
        this.children = children;
    }
}
