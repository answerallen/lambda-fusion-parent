package com.lambda.fusion.dict.support.resolve;

import com.lambda.fusion.dict.entity.DictType;
import com.lambda.fusion.dict.support.model.DynamicDict;
import java.util.List;

/**
 * @author Jin
 */
public interface IDynamicDictResolve {

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
     * @param dictType 配置信息
     * @return 列表
     */
    List<DynamicDict> doResolve(DictType dictType);
}
