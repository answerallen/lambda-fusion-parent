package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.domain.area.Area;
import com.lambda.fusion.authority.domain.area.AreaEntity;
import com.lambda.fusion.authority.domain.area.AreaQuery;
import com.lambda.fusion.authority.domain.area.AreaTree;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 行政区划数据持久层接口
 */
@Mapper
public interface AreaMapper extends BaseMapper<AreaEntity> {

    /**
     * 条件查询区域列表
     *
     * @param parameters 查询参数
     * @return 区域列表
     */
    List<Area> selectAreas(@Param("parameters") AreaQuery parameters);

    /**
     * 获取区域树形结构
     *
     * @param parentCode 父区域编码，null表示查询所有
     * @return 树形结构列表
     */
    List<AreaTree> selectAreaTree(@Param("parentCode") String parentCode);

    /**
     * 根据区域编码查询
     *
     * @param areaCode 区域编码
     * @return 区域信息
     */
    Area selectByAreaCode(@Param("areaCode") String areaCode);

    /**
     * 查询下级区域
     *
     * @param parentCode 父区域编码
     * @return 下级区域列表
     */
    List<Area> selectChildren(@Param("parentCode") String parentCode);

    /**
     * 检查区域编码是否存在
     *
     * @param areaCode 区域编码
     * @return 是否存在
     */
    boolean hasExists(@Param("areaCode") String areaCode);

    /**
     * 检查是否存在子区域
     *
     * @param parentCode 父区域编码
     * @return 是否存在子区域
     */
    boolean hasChildren(@Param("parentCode") String parentCode);

    /**
     * 根据区域编码删除
     *
     * @param areaCode 区域编码
     */
    void deleteByAreaCode(@Param("areaCode") String areaCode);
}
