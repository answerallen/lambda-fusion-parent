package com.lambda.fusion.authority.login.domain;

import lombok.Data;

@Data
public class NavigationParameter {
    /**
     * 父级菜单，只返回此菜单下的数据
     */
    String parentId;

    /**
     * 制定菜单层级
     */
    Integer level;

    /**
     * 资源模式
     */
    int mode;

    Integer model;
    /**
     * 名称
     */
    String name;

    /**
     * 显示所有菜单，0：不显示，1：显示
     */
    int all = 0;

    public int getMode() {
        if (null != model) {
            this.mode = model;
        }
        return mode;
    }
}
