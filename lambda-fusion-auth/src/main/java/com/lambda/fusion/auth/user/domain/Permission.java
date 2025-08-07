package com.lambda.fusion.auth.user.domain;

import com.lambda.fusion.core.tree.Tree;
import java.util.List;
import lombok.Data;

@Data
public class Permission implements Tree<Permission> {

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
