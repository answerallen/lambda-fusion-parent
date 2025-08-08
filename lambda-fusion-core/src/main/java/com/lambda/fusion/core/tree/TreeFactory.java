package com.lambda.fusion.core.tree;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.lambda.fusion.core.Constants;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cglib.core.internal.Function;
import org.springframework.util.StopWatch;

/**
 * 更高效的树形数据构建器
 *
 */
@Slf4j
public final class TreeFactory {

    private TreeFactory() {}

    /**
     * <p>一个列表数据构建成一棵树形数据，对象需实现{@link Tree}接口</p>
     *
     * @param target 元数据列表
     * @param <T>    实现Tree接口的对象
     * @return 树
     */
    public static <T extends Tree<T>> List<T> build(List<T> target) {
        return build2(target, Tree::id, Tree::pid, Tree::children);
    }

    /**
     * 构建树形数据，此方法不需要实现接口。
     * 会生成空的children对象
     *
     * @param target  元数据列表
     * @param idFc    主键标识字段
     * @param pidFc   父标识字段
     * @param childFc 子节点保存位置
     * @param <T>     原始对象
     * @return 树
     */
    public static <T> List<T> build(
            List<T> target, Function<T, Object> idFc, Function<T, Object> pidFc, BiConsumer<T, List<T>> childFc) {
        if (CollectionUtils.isEmpty(target)) {
            return new ArrayList<>();
        }
        StopWatch clock = null;
        if (log.isTraceEnabled()) {
            clock = new StopWatch();
            clock.start();
        }
        List<T> result = new ArrayList<>(target.size() / 2);
        Map<Object, List<T>> idMap = Maps.newHashMapWithExpectedSize(target.size());
        Set<Object> parent = new HashSet<>();

        target.forEach(obj -> {
            Object pid = pidFc.apply(obj);
            if (idMap.containsKey(pid)) {
                List<T> list = idMap.get(pid);
                list.add(obj);
            } else {
                List<T> list = new ArrayList<>();
                list.add(obj);
                idMap.put(pid, list);
                parent.add(pid);
            }
            Object id = idFc.apply(obj);
            if (idMap.containsKey(id)) {
                childFc.accept(obj, idMap.get(id));
                parent.remove(id);
            } else {
                List<T> list = new ArrayList<>();
                childFc.accept(obj, list);
                idMap.put(id, list);
            }
        });
        parent.forEach(item -> result.addAll(idMap.get(item)));
        if (log.isTraceEnabled() && clock != null) {
            clock.stop();
            log.trace(Constants.LOG_TREE_BUILD_TIME, clock.getTotalTimeNanos());
        }
        return result;
    }

    /**
     * 构建树形数据，不会生成空的children对象
     *
     * @param target 元数据列表
     * @param <T>    原始对象
     * @return 树
     */
    public static <T> List<T> build2(
            List<T> target, Function<T, Object> idFc, Function<T, Object> pidFc, BiConsumer<T, List<T>> childFc) {
        if (CollectionUtils.isEmpty(target)) {
            return Collections.emptyList();
        }
        StopWatch clock = null;
        if (log.isTraceEnabled()) {
            clock = new StopWatch();
            clock.start();
        }

        // 存储所有对象
        Map<Object, T> objects = Maps.newHashMapWithExpectedSize(target.size());
        // 存储临时映射关系
        SetMultimap<Object, Object> temps = HashMultimap.create();
        // 存储所有节点的子集关系
        ArrayListMultimap<Object, T> childrens = ArrayListMultimap.create();
        // 维护所有顶级节点
        List<Object> list1 = new ArrayList<>();

        target.forEach(obj -> {
            Object id = idFc.apply(obj);
            Object pid = pidFc.apply(obj);
            objects.put(id, obj);

            if (Objects.isNull(pid)) {
                list1.add(id);
            } else {
                // 当map1中包含pid时代表pid所对应的对象已经遍历过
                if (objects.containsKey(pid)) {
                    childrens.put(pid, obj);
                    childFc.accept(objects.get(pid), childrens.get(pid));
                    list1.remove(id);
                } else {
                    // 1.顶级节点
                    list1.add(id);
                    // 2.尚未遍历，暂存
                    childrens.put(pid, obj);
                    temps.put(pid, id);
                }
            }
            // 将提前遍历的子节点
            if (childrens.containsKey(id)) {
                childFc.accept(obj, childrens.get(id));
                list1.removeAll(temps.get(id));
            }
        });
        List<T> result = list1.stream().map(objects::get).collect(Collectors.toList());
        if (log.isTraceEnabled() && clock != null) {
            clock.stop();
            log.trace(Constants.LOG_TREE_BUILD_TIME, clock.getTotalTimeNanos());
        }
        return result;
    }

    public static <T> List<T> build3(
            List<T> target,
            Function<T, Object> idFc,
            Function<T, Object> pidFc,
            Function<T, Integer> rankFc,
            BiConsumer<T, List<T>> childFc) {
        if (CollectionUtils.isEmpty(target)) {
            return Collections.emptyList();
        }
        StopWatch clock = null;
        if (log.isTraceEnabled()) {
            clock = new StopWatch();
            clock.start();
        }

        Integer topRank = Integer.MAX_VALUE;
        // rank值为key, 倒序排列
        TreeMap<Integer, List<T>> rankMap = Maps.newTreeMap((o1, o2) -> o2 - o1);
        // pid + rank值为key
        SetMultimap<String, T> children = HashMultimap.create();

        for (T obj : target) {
            Integer rank = rankFc.apply(obj);
            if (rank < topRank) {
                topRank = rank;
            }
            if (rankMap.containsKey(rank)) {
                rankMap.get(rank).add(obj);
            } else {
                rankMap.put(rank, new ArrayList<>(Collections.singletonList(obj)));
            }
            // 非顶级节点需要把自身放入children中, key为pid + 父级rank值
            children.put(pidFc.apply(obj) + Constants.DOT + (rank - 1), obj);
        }

        rankMap.forEach((rank, list) -> {
            list.forEach(obj -> {
                String key = idFc.apply(obj) + Constants.DOT + rankFc.apply(obj);
                if (children.containsKey(key)) {
                    childFc.accept(obj, new ArrayList<>(children.get(key)));
                }
            });
        });

        List<T> result = rankMap.get(topRank);

        if (log.isTraceEnabled() && clock != null) {
            clock.stop();
            log.trace(Constants.LOG_TREE_BUILD_TIME, clock.getTotalTimeNanos());
        }
        return result;
    }
}
