package com.lambda.fusion.core.tree.filter;

import com.google.common.collect.Maps;
import com.lambda.fusion.core.FusionConstants;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * DefaultTreeDataFilter
 *
 * @author Jin
 */
@Slf4j
public class DefaultTreeDataFilter implements TreeDataFilter {

    @Override
    public <T> List<T> filter(
            List<T> target,
            String queryStr,
            Function<T, String> queryFc,
            Function<T, String> idFc,
            Function<T, String> pFullKey,
            Function<Collection<T>, List<T>> sort) {
        // 结果集
        final Set<T> result = new HashSet<>();
        if (StringUtils.isBlank(queryStr)) {
            result.addAll(target);
            return sort.apply(result);
        }
        try {
            // 查询参数映射
            Map<String, T> paramMap = Maps.newHashMap();
            // ID 映射
            Map<String, T> idMap = target.stream().collect(Collectors.toMap(idFc, v -> v));
            // 完整key映射
            final MultiValueMap<String, T> fullKeyMap = new LinkedMultiValueMap<>();
            for (T f : target) {
                fullKeyMap.add(pFullKey.apply(f), f);
            }
            // 过滤包含请求参数的对象
            for (T f : target) {
                if (queryFc.apply(f).contains(queryStr)) {
                    paramMap.put(queryFc.apply(f), f);
                }
            }
            // 对查到的数据进行字父级数据查询
            for (T f : paramMap.values()) {
                // 对完整路径拆分出父级
                final String pKey = pFullKey.apply(f);
                boolean isTop = true;
                if (StringUtils.isNotEmpty(pKey)) {
                    isTop = false;
                    final StringTokenizer tokenizer = new StringTokenizer(pKey, FusionConstants.JOINER);
                    while (tokenizer.hasMoreTokens()) {
                        T t = idMap.get(tokenizer.nextToken());
                        if (t != null) {
                            result.add(t);
                        }
                    }
                }
                // 放入当前层级
                result.add(f);
                // 放入子级
                String fixKey = isTop ? idFc.apply(f) : pFullKey.apply(f) + FusionConstants.JOINER + idFc.apply(f);
                if (StringUtils.isNotEmpty(fixKey)) {
                    for (Map.Entry<String, List<T>> entry : fullKeyMap.entrySet()) {
                        String key = entry.getKey();
                        if (Objects.nonNull(key) && key.startsWith(fixKey)) {
                            List<T> list = fullKeyMap.get(key);
                            if (CollectionUtils.isNotEmpty(list)) {
                                result.addAll(list);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.info("过滤数据发生异常,过滤参数,{}", queryStr, e);
            return target;
        }
        return sort.apply(result);
    }
}
