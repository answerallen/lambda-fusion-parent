package com.lamuda.cloud.scaffold.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lamuda.cloud.fx.dict.entity.DictInfoGroup;
import com.lamuda.cloud.fx.dict.entity.DictInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 多级数据字典详细信息
 * @author jin
 */
@Mapper
public interface DictInfoMapper extends BaseMapper<DictInfo> {

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
    Page<DictInfo> page(Page<DictInfo> pagination, @Param("parameters") Map<String, Object> parameters);

    /**
     * 条件查询
     * @param parameters
     * @return
     */
    List<DictInfo> selectDictInfo(@Param("parameters") Map<String, Object> parameters);


    /**
     * 获取数据项列表
     * @param dictInfo
     * @return
     */
    List<DictInfo> getDictInfoList(@Param("dictInfo") DictInfo dictInfo);

    /**
     * 根据parentKeys构建树形数据项
     * @param ids
     * @param tenantId
     * @return
     */
    List<DictInfo> treeList(@Param("ids")List<String> ids, @Param("tenantId")String tenantId);

}
