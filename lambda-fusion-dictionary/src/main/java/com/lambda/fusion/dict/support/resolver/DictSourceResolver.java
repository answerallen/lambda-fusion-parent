package com.lambda.fusion.dict.support.resolver;

import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.DynamicDictSource;
import java.util.List;

/**
 * @author Jin
 */
public interface DictSourceResolver {

    /**
     * 能否解析
     * @param valueType  字典值类型
     * @return  true | false(default)
     */
    default boolean isSupport(Integer valueType) {
        return false;
    }

    /**
     *  解析动态字典
     * @param dictTypeTree 配置信息
     * @return 列表
     */
    List<DynamicDictSource> doResolve(DictTypeTree dictTypeTree);
}
