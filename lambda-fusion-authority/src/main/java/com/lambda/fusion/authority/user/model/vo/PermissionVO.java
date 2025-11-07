package com.lambda.fusion.authority.user.model.vo;

import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings({"EI_EXPOSE_REP", "UUF_UNUSED_FIELD", "CT_CONSTRUCTOR_THROW"})
public class PermissionVO implements TreeNode<PermissionVO> {

    private String id;

    private String name;

    private String pid;

    private List<PermissionVO> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<PermissionVO> children) {
        this.children = children;
    }
}
