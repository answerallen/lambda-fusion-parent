package com.lamuda.cloud.scaffold.dict.support.resolve;


import com.lamuda.cloud.scaffold.dict.support.model.DynamicDict;
import com.lamuda.cloud.fx.dict.entity.DictType;

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
