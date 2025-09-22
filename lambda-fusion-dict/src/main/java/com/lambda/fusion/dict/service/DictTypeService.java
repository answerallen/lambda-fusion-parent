package com.lambda.fusion.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.dict.model.dto.QueryDictTree;
import com.lambda.fusion.dict.model.entity.DictType;
import java.util.List;

/**
 * 多级数据字典类型
 *
 * @author Jin
 */
public interface DictTypeService extends IService<DictType> {

    /**
     * 保存多级数据字典类型
     *
     * @param dictType
     * @return
     */
    DictType saveDictType(DictType dictType);

    /**
     * 更新多级数据字典类型
     *
     * @param dictType
     * @return
     */
    void updateDictType(DictType dictType);

    /**
     * 获取动态字典树形分类
     *
     * @param queryDictTree
     * @return
     */
    List<DictType> dynamicTreeList(QueryDictTree queryDictTree);

    /**
     * 删除多级字典类型
     *
     * @param id
     */
    void deleteDictType(String id);

    /**
     * 获取所有字典分类
     *
     * @param type
     * @return
     */
    List<DictType> getDictTypeList(String type);

    /**
     * 查询SQL类型字典
     *
     * @param dictType 类型ID
     * @return 结果集
     */
    DictType dynamicDict(String dictType);
}
