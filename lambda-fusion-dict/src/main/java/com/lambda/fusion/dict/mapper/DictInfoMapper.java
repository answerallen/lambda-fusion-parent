package com.lambda.fusion.dict.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.dict.model.DictInfoGroup;
import com.lambda.fusion.dict.model.DictionaryEntry;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 多级数据字典详细信息
 * @author jin
 */
@Mapper
public interface DictInfoMapper extends BaseMapper<DictionaryEntry> {

    /**
     * 获取所有启用的字典
     * @param type
     * @param tenantId
     * @return
     */
    List<DictInfoGroup> getAllDictInfoGroup(@Param("type") String type, @Param("tenantId") String tenantId);

    /**
     * 分页查询字典详细信息
     * @param pagination
     * @param parameters
     * @return
     */
    Page<DictionaryEntry> page(Page<DictionaryEntry> pagination, @Param("parameters") Map<String, Object> parameters);

    /**
     * 条件查询
     * @return
     */
    List<DictionaryEntry> selectDictInfo(@Param(Constants.WRAPPER) LambdaQueryWrapper<DictionaryEntry> wrapper);

    /**
     * 获取数据项列表
     * @param dictionaryEntry
     * @return
     */
    List<DictionaryEntry> getDictInfoList(@Param("dictInfo") DictionaryEntry dictionaryEntry);

    /**
     * 根据parentKeys构建树形数据项
     * @param ids
     * @param tenantId
     * @return
     */
    List<DictionaryEntry> treeList(@Param("ids") List<String> ids, @Param("tenantId") String tenantId);
}
