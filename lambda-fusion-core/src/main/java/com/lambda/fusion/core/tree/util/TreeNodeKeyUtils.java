package com.lambda.fusion.core.tree.util;

import com.google.common.collect.Maps;
import com.lambda.fusion.core.tree.model.TreeNodeKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/**
 * 数据构建完整父key路径, 一般用于构建树形数据之前
 * @author jin
 */
@UtilityClass
public class TreeNodeKeyUtils {

    /**
     *
     * @param target 平铺数据
     * @param idFc id字段
     * @param pIdFc  父ID字段
     * @param fullParentKeys     存储父KEY完整路径
     * @param <T>  结构数据
     * @return ig
     */
    public static <T> List<T> buildTreeFullKeys(
            List<T> target, Function<T, Object> idFc, Function<T, Object> pIdFc, BiConsumer<T, String> fullParentKeys) {
        Map<Object, TreeNodeKey> pKeys = Maps.newHashMapWithExpectedSize(target.size());
        // 构建父级Key
        target.forEach(v -> {
            final Object pId = pIdFc.apply(v);
            final TreeNodeKey pidKey = pKeys.getOrDefault(pId, new TreeNodeKey(pId, null));
            pKeys.put(pId, pidKey);
            final Object id = idFc.apply(v);
            final TreeNodeKey key = pKeys.getOrDefault(id, new TreeNodeKey(id, null));
            key.setParentKey(pidKey);
            pKeys.put(id, key);
        });

        target.forEach(v -> {
            final Object pId = pIdFc.apply(v);
            if (Objects.nonNull(pId)) {
                fullParentKeys.accept(v, pKeys.get(pId).getKey());
            }
        });
        return target;
    }
}
