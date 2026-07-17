package com.lambda.fusion.core.tree.util;

import static com.lambda.fusion.core.tree.model.TreeDragMode.BEFORE;
import static com.lambda.fusion.core.tree.model.TreeDragMode.CHILD;

import com.google.common.collect.Lists;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.tree.TreeNode;
import com.lambda.fusion.core.tree.model.TreeDragMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * TreeNodeUtils
 *
 * @author Jin
 */
@UtilityClass
public final class TreeNodeUtils {

    /**
     * 获取拖动后所有修改的对象
     *
     * @param resource
     * @param target
     * @param mode
     * @param directChildrenGetter
     * @param allChildrenGetter
     * @return java.util.List<T>
     */
    public static <T extends TreeNode<T>> List<T> getAllChangedAfterMoved(
            @Nonnull T resource,
            @Nonnull T target,
            TreeDragMode mode,
            Function<String, List<T>> directChildrenGetter,
            Function<String, List<T>> allChildrenGetter) {
        final String id1 = resource.id();
        final String id2 = target.id();
        final String pid2 = target.pid();
        final String parentKeys1 = resource.parentKeys();
        List<T> changed = Lists.newArrayList();
        boolean differently = CHILD.equals(mode);
        List<T> children = directChildrenGetter.apply(CHILD.equals(mode) ? id2 : pid2);
        Optional<T> optional1 =
                children.stream().filter(item -> item.id().equals(id1)).findFirst();
        Optional<T> optional2 =
                children.stream().filter(item -> item.id().equals(id2)).findFirst();
        T source1 = optional1.orElse(resource);
        T target1 = optional2.orElse(target);

        if (CHILD.equals(mode)) {
            String parentKeys = generateParentkeys(target1.parentKeys(), target1.id());
            source1.pid(id2);
            source1.parentKeys(parentKeys);
            source1.level(level(parentKeys));
            children.addFirst(source1);
        } else {
            if (optional1.isPresent()) {
                children.remove(source1);
            } else {
                differently = true;
                String parentKeys = target1.parentKeys();
                source1.pid(pid2);
                source1.parentKeys(parentKeys);
                source1.level(level(parentKeys));
            }
            int index = children.indexOf(target1);
            children.add(BEFORE.equals(mode) ? index : index + 1, source1);
        }
        // 兼容排序号可能未初始化或不正确的问题
        int i = 0;
        for (T item : children) {
            if (item.order() != i || Objects.equals(item.id(), source1.id())) {
                item.order(i);
                changed.add(item);
            }
            i++;
        }
        if (differently) {
            List<T> children2 = allChildrenGetter.apply(generateParentkeys(parentKeys1, id1));
            changed.addAll(childrenHandler(parentKeys1, source1.parentKeys(), children2));
        }

        return changed;
    }

    public static int level(String parentKeys) {
        if (StringUtils.isBlank(parentKeys)) {
            return 0;
        }
        return StringUtils.split(parentKeys, FusionConstants.JOINER).length;
    }

    /***
     * 生成parentKeys
     * @param parentKeys
     * @param id
     * @return java.lang.String
     *
     */
    private static String generateParentkeys(String parentKeys, String id) {
        return StringUtils.isNotBlank(parentKeys) ? parentKeys + FusionConstants.JOINER + id : id;
    }

    /***
     * 处理需要更新parentKeys属性的对象
     * @param searchString
     * @param replacement
     * @param children2
     *
     */
    private static <T extends TreeNode<T>> List<T> childrenHandler(
            String searchString, String replacement, List<T> children2) {
        List<T> changed2 = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(children2)) {
            for (T item : children2) {
                String result = replace(item, searchString, replacement);
                item.parentKeys(result);
                item.level(level(result));
                changed2.add(item);
            }
        }
        return changed2;
    }

    private static <T extends TreeNode<T>> String replace(T item, String searchString, String replacement) {
        String result;
        if (StringUtils.isNotBlank(searchString)) {
            if (StringUtils.isNotBlank(replacement)) {
                result = StringUtils.replace(item.parentKeys(), searchString, replacement);
            } else {
                result = StringUtils.removeStart(item.parentKeys(), searchString + FusionConstants.JOINER);
            }
        } else if (StringUtils.isNotBlank(replacement)) {
            result = replacement + FusionConstants.JOINER + item.parentKeys();
        } else {
            result = item.parentKeys();
        }
        return result;
    }
}
