package com.lambda.fusion.authority.model.user;

import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.Data;

@Data
@SuppressFBWarnings({"EI_EXPOSE_REP", "UUF_UNUSED_FIELD", "CT_CONSTRUCTOR_THROW"})
public class Permission implements TreeNode<Permission> {

    private String id;

    private String name;

    private String pid;

    private List<Permission> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public void children(List<Permission> children) {
        this.children = children;
    }
}
