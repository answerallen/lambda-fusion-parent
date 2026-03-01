package com.lambda.fusion.authority.model.area;

import com.lambda.fusion.core.tree.TreeNode;
import java.util.List;
import lombok.Data;

/**
 * 行政区划树形结构VO
 */
@Data
public class AreaTree implements TreeNode<AreaTree> {

    /**
     * 数据库ID
     */
    private Long dbId;

    /**
     * 区域编码
     */
    private String areaCode;

    /**
     * 区域名称
     */
    private String name;

    /**
     * 上级区域编码
     */
    private String parentCode;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 类型
     */
    private String type;

    /**
     * 子节点
     */
    private List<AreaTree> children;

    @Override
    public String id() {
        return this.areaCode;
    }

    @Override
    public String pid() {
        return this.parentCode;
    }

    @Override
    public void children(List<AreaTree> children) {
        this.children = children;
    }
}
