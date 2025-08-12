package com.lambda.fusion.core.tree;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * ITreeDataFilter
 * @author Jin
 */
public interface ITreeDataFilter {
    /**
     * 对树进行数据过滤， 实例：
     * <pre>
     *     {@code
     *       public List<Organization> treeList(Parameters parameters) {
     *         List<Organization> list = OrganizationMapper.getAllMutableOrgan(parameters);
     *         if (parameters.getSelf() != null) {
     *             list.add(parameters.getSelf());
     *         }
     *         final List<Organization> results = treeDataFilter.filter(list, parameters.getName(), Organization::getName, Organization::getId, Organization::getParentKeys,
     *                 (target) -> target.stream().sorted(Comparator.comparing(Organization::getLevel).thenComparing(Organization::getName)).collect(Collectors.toList()));
     *         return TreeFactory.build(results);
     *     }
     *     }
     * </pre>
     *
     * @param target   数据源
     * @param queryStr 查询字符串
     * @param queryFc  查询字段
     * @param idFc     id字段
     * @param pFullKey 完整路径字段
     * @param sort     排序
     * @param <T>      有子父节点的对象
     * @return ig
     */
    <T> List<T> filter(
            List<T> target,
            String queryStr,
            Function<T, String> queryFc,
            Function<T, String> idFc,
            Function<T, String> pFullKey,
            Function<Collection<T>, List<T>> sort);
}
