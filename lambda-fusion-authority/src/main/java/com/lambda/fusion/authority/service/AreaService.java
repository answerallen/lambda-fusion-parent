package com.lambda.fusion.authority.service;

import com.lambda.fusion.authority.model.area.Area;
import com.lambda.fusion.authority.model.area.AreaQuery;
import com.lambda.fusion.authority.model.area.AreaTree;
import com.lambda.fusion.authority.model.area.CreateArea;
import com.lambda.fusion.authority.model.area.UpdateArea;
import java.util.List;

/**
 * 行政区划服务接口
 */
public interface AreaService {

    /**
     * 条件查询区域列表
     *
     * @param query 查询参数
     * @return 区域列表
     */
    List<Area> getAreas(AreaQuery query);

    /**
     * 获取区域树形结构
     *
     * @param parentCode 父区域编码，null表示获取顶级
     * @return 树形结构列表
     */
    List<AreaTree> getAreaTree(String parentCode);

    /**
     * 根据区域编码查询
     *
     * @param areaCode 区域编码
     * @return 区域信息
     */
    Area getByAreaCode(String areaCode);

    /**
     * 获取下级区域
     *
     * @param parentCode 父区域编码
     * @return 下级区域列表
     */
    List<Area> getChildren(String parentCode);

    /**
     * 新增区域
     *
     * @param createArea 创建参数
     * @return 新增的区域信息
     */
    Area addArea(CreateArea createArea);

    /**
     * 更新区域
     *
     * @param updateArea 更新参数
     * @return 更新后的区域信息
     */
    Area updateArea(UpdateArea updateArea);

    /**
     * 删除区域
     *
     * @param areaCode 区域编码
     */
    void deleteArea(String areaCode);

    /**
     * 检查区域编码是否存在
     *
     * @param areaCode 区域编码
     * @return 是否存在
     */
    boolean checkAreaCode(String areaCode);
}
