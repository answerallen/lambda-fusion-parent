package com.lambda.fusion.auth.resource.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.fusion.core.tree.Tree;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "资源信息")
public class Resource extends MutableResource implements Tree<Resource> {

    @Schema(description = "子资源集合")
    private List<Resource> children;

    @Schema(description = "按钮集合")
    private List<Button> buttons;

    @JsonIgnore
    public List<Resource> getChildrenOrDefault() {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        return this.children;
    }

    @JsonIgnore
    public List<Button> getButtonsOrDefault() {
        if (buttons == null) {
            this.buttons = new ArrayList<>();
        }
        return this.buttons;
    }

    @Override
    public String id() {
        return this.getId();
    }

    @Override
    public String pid() {
        return this.getParentId();
    }

    @Override
    public void children(List<Resource> children) {
        this.children = children;
    }
}
