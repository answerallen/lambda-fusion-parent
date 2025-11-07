package com.lambda.fusion.authority.organization.model.vo;

import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "组织机构")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class OrganizationTreeVO implements TreeNode<OrganizationTreeVO> {

    @Schema(description = "主键ID")
    String id;

    @Schema(description = "组织名称")
    String name;

    @Schema(description = "上级编号")
    String pid;

    @Schema(description = "下级节点")
    List<OrganizationTreeVO> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<OrganizationTreeVO> children) {
        this.children = children;
    }
}
