package com.lambda.fusion.authority.role.model.vo;

import com.lambda.fusion.core.tree.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class AccessPermissionVO implements TreeNode<AccessPermissionVO> {

    @Schema(description = "权限编号")
    private String id;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "上级编号")
    private String parentId;

    @Schema(description = "是否选中")
    private Boolean checked;

    private int type;

    @Schema(description = "下级权限")
    private List<AccessPermissionVO> children;

    @Schema(description = "按钮权限")
    private List<AccessPermissionVO> buttons;

    private Integer status;

    @Override
    public String id() {
        return getId();
    }

    @Override
    public String pid() {
        return getParentId();
    }

    @Override
    public void children(List<AccessPermissionVO> children) {
        this.children = children;
    }
}
