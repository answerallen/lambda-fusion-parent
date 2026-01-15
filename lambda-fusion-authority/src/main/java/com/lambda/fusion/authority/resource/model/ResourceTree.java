package com.lambda.fusion.authority.resource.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "资源信息")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ResourceTree extends Resource implements TreeNode<ResourceTree> {

    @Schema(description = "路由组件")
    private String component;

    @Schema(description = "路由元信息")
    private Map<String, Object> meta;

    @Schema(description = "路由名称")
    @JsonProperty("name")
    @Override
    public String getResName() {
        return super.getResName();
    }

    @Schema(description = "路由路径")
    @JsonProperty("path")
    @Override
    public String getResPath() {
        return super.getResPath();
    }

    @Schema(description = "子资源集合")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ResourceTree> children;

    @Schema(description = "按钮集合")
    private List<Button> buttons;

    @JsonIgnore
    public List<ResourceTree> getChildrenOrDefault() {
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
    public void children(List<ResourceTree> children) {
        this.children = children;
    }
}
