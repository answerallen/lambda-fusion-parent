package com.lambda.fusion.dict.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.dict.model.vo.DictInfoVO;
import com.lambda.fusion.dict.model.entity.DictInfoGroup;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 多级数据字典详细信息
 * @author jin
 */
@Mapper
public interface DictInfoMapper extends BaseMapper<DictInfoVO> {

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
    Page<DictInfoVO> page(Page<DictInfoVO> pagination, @Param("parameters") Map<String, Object> parameters);

    /**
     * 条件查询
     * @return
     */
    List<DictInfoVO> selectDictInfo(@Param(Constants.WRAPPER) LambdaQueryWrapper<DictInfoVO> wrapper);

    /**
     * 获取数据项列表
     * @param dictInfoVO
     * @return
     */
    List<DictInfoVO> getDictInfoList(@Param("dictInfo") DictInfoVO dictInfoVO);

    /**
     * 根据parentKeys构建树形数据项
     * @param ids
     * @param tenantId
     * @return
     */
    List<DictInfoVO> treeList(@Param("ids") List<String> ids, @Param("tenantId") String tenantId);
}
