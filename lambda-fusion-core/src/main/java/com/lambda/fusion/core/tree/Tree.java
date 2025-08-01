package com.lambda.fusion.core.tree;

import com.lambda.cloud.core.exception.NotSupportedException;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/**
 * 树元数据标识，使用{@link TreeFactory#build(List)}
 * 即可将一个列表数据构建成一棵树形数据
 *
 * @author Jin
 */
public interface Tree<T> {
    String SPLIT = "-";

    /**
     * 数据的唯一标识对应的属性
     *
     * @return id字段
     */
    String id();

    /**
     * 父数据标识对应的属性
     *
     * @return 父id字段
     */
    String pid();

    /**
     * 获取父节点列表
     *
     * @return
     */
    default String parentkeys() {
        return null;
    }

    /**
     * 存储子节点对应的List属性
     *
     * @param children children的数据集合
     */
    void children(List<T> children);

    /***
     * 构造默认父节点列表
     * @return java.lang.String
     *
     */
    default String buildParentKeys() {
        String pid = this.id();
        String keys = this.parentkeys();
        if (StringUtils.isNotBlank(keys)) {
            return keys + SPLIT + pid;
        }
        return pid;
    }

    /**
     * 设置父节点ID
     *
     * @param pid
     *
     */
    default void pid(String pid) {
        throw new NotSupportedException();
    }

    /**
     * 获取节点级别
     *
     * @param
     * @return int
     *
     */
    default int level() {
        throw new NotSupportedException();
    }

    /**
     * 设置节点级别
     *
     * @param level
     * @author Jin
     */
    default void level(int level) {
        throw new NotSupportedException();
    }

    /**
     * 获取节点顺序
     *
     * @param
     * @return int
     *
     */
    default int order() {
        throw new NotSupportedException();
    }

    /**
     * 设置节点顺序
     *
     * @param order
     *
     */
    default void order(int order) {
        throw new NotSupportedException();
    }

    /**
     * 设置节点所有父节点
     *
     * @param parentkeys
     * @return void
     *
     */
    default void parentkeys(String parentkeys) {
        throw new NotSupportedException();
    }
}
