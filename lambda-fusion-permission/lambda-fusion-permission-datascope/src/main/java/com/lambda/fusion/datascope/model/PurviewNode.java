package com.lambda.fusion.datascope.model;

import com.lambda.fusion.core.tree.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "通用权限树节点")
public class PurviewNode implements TreeNode<PurviewNode> {

    @Schema(description = "节点ID")
    private String id;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "父节点ID")
    private String pid;

    @Schema(description = "层级")
    private Integer level;

    @Schema(description = "是否末级节点")
    private Boolean lastStage;

    @Schema(description = "选中状态: 0未选, 1全选, 2半选")
    private Integer checked;

    @Schema(description = "扩展属性")
    private Map<String, Object> props = new HashMap<>();

    @Schema(description = "子节点")
    private List<PurviewNode> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<PurviewNode> children) {
        this.children = children;
    }
}
