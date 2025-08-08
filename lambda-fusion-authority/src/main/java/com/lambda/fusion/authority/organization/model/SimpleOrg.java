package com.lambda.fusion.authority.organization.model;

import com.lambda.fusion.core.tree.Tree;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "组织机构")
public class SimpleOrg implements Tree<SimpleOrg> {

    @Schema(description = "主键ID")
    String id;

    @Schema(description = "组织名称")
    String name;

    @Schema(description = "上级编号")
    String pid;

    @Schema(description = "下级节点")
    List<SimpleOrg> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<SimpleOrg> children) {
        this.children = children;
    }
}
