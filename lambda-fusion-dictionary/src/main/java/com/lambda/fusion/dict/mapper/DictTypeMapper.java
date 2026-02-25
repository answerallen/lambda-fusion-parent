package com.lambda.fusion.dict.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.dict.model.DictTypeTree;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 多级数据字典类型
 *
 * @author Jin
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeTree> {

    /**
     * 获取所有字典分类
     *
     * @param type
     * @return
     */
    List<DictTypeTree> getDictTypeList(@Param("type") String type);

    /**
     * 根据字典类型查询上一级字典类型
     *
     * @param type
     * @return
     */
    DictTypeTree selectParentByType(String type);

    /**
     * 获取树形字典分类
     *
     * @param parameters
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<DictTypeTree> treeList(@Param("parameters") Map<String, Object> parameters);

    /**
     * 获取某个字典的级联的字典分类列表
     *
     * @param id
     * @return
     */
    List<DictTypeTree> getCascadeDictType(String id);
}
