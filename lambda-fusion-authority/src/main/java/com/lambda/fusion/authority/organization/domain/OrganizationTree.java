package com.lambda.fusion.authority.organization.domain;

import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "组织机构")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class OrganizationTree implements TreeNode<OrganizationTree> {

    @Schema(description = "主键ID")
    String id;

    @Schema(description = "组织名称")
    String name;

    @Schema(description = "上级编号")
    String pid;

    @Schema(description = "下级节点")
    List<OrganizationTree> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<OrganizationTree> children) {
        this.children = children;
    }
}
