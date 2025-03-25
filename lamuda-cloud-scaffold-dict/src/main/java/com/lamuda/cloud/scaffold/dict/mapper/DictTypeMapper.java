package com.lamuda.cloud.scaffold.dict.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lamuda.cloud.fx.dict.entity.DictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 多级数据字典类型
 *
 * @author Jin
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictType> {

    /**
     * 获取所有字典分类
     *
     * @param type
     * @return
     */
    List<DictType> getDictTypeList(@Param("type") String type);

    /**
     * 分页查询数据分类
     *
     * @param pagination
     * @param parameters
     * @return
     */
    Page<DictType> pageDictType(Page<DictType> pagination, @Param("parameters") Map<String, String> parameters);

    /**
     * 根据字典类型查询上一级字典类型
     *
     * @param type
     * @return
     */
    DictType selectParentByType(String type);

    /**
     * 获取树形字典分类
     *
     * @param parameters
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<DictType> treeList(@Param("parameters") Map<String, Object> parameters);

    /**
     * 获取某个字典的级联的字典分类列表
     *
     * @param id
     * @return
     */
    List<DictType> getCascadeDictType(String id);
}
