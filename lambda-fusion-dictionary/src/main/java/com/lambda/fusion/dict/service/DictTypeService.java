package com.lambda.fusion.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.QueryDictTree;
import java.util.List;

/**
 * 多级数据字典类型
 *
 * @author Jin
 */
public interface DictTypeService extends IService<DictTypeTree> {

    /**
     * 保存多级数据字典类型
     *
     * @param dictTypeTree
     * @return
     */
    DictTypeTree saveDictType(DictTypeTree dictTypeTree);

    /**
     * 更新多级数据字典类型
     *
     * @param dictTypeTree
     * @return
     */
    void updateDictType(DictTypeTree dictTypeTree);

    /**
     * 获取动态字典树形分类
     *
     * @param queryDictTree
     * @return
     */
    List<DictTypeTree> compositeTreeList(QueryDictTree queryDictTree);

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
    List<DictTypeTree> getDictTypeList(String type);

    /**
     * 查询SQL类型字典
     *
     * @param dictType 类型ID
     * @return 结果集
     */
    DictTypeTree compositeDict(String dictType);
}
