package com.lambda.fusion.authority.role.bean;

import com.lambda.fusion.authority.resource.model.Button;
import com.lambda.fusion.core.tree.Tree;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "资源树节点信息")
public class RoleAuthority implements Tree<RoleAuthority> {

    @Schema(required = true, description = "父节点编号")
    private String id;

    @Schema(required = true, description = "资源名称")
    private String name;

    @Schema(required = true, description = "父节点编号")
    private String parentId;

    @Schema(description = "是否选中")
    private Boolean checked;

    @Schema(description = "下级子节点信息")
    private List<RoleAuthority> children;

    @Schema(description = "按钮集合")
    private List<Button> buttons;

    @Schema(description = "模式-0:后台资源,1:APP资源")
    private Integer mode;

    @Schema(description = "资源类型")
    private int type;

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String pid() {
        return this.parentId;
    }

    @Override
    public void children(List<RoleAuthority> children) {
        this.children = children;
    }
}
