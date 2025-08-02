package com.lambda.fusion.dict.common.resolve;

import com.lambda.fusion.dict.common.model.DynamicDict;
import com.lambda.fusion.dict.dao.entity.DictType;
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
