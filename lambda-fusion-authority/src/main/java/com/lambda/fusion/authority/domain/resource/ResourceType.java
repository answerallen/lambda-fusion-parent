package com.lambda.fusion.authority.domain.resource;

public enum ResourceType {
    /**
     * 服务
     */
    SERVICE,
    /**
     * 菜单
     */
    MENU,
    /**
     * 外链
     */
    EXTERNAL_LINK,
    /**
     * 按钮
     */
    BUTTON;

    public static ResourceType get(int i) {
        ResourceType[] values = ResourceType.values();
        if (i < values.length) {
            return values[i];
        }
        return null;
    }

    public Boolean isButton() {
        return BUTTON.equals(this);
    }
}
