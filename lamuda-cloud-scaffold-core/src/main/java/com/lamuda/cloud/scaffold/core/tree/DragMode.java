package com.lamuda.cloud.scaffold.core.tree;


/**
 * DragMode
 *
 * @author jin
 */
public enum DragMode {
    /**
     * 拖动节点使之成为目标节点的子节点
     */
    CHILD,
    /**
     * 拖动节点到目标节点之前
     */
    BEFORE,
    /**
     * 拖动节点到目标节点之后
     */
    AFTER;


    public static DragMode valueOf(int i) {
        DragMode[] values = DragMode.values();
        if (i >= 0 && i < values.length) {
            return values[i];
        }
        throw new IllegalArgumentException("wrong index of DragMode, allowed: [0, 1, 2]");
    }
}
