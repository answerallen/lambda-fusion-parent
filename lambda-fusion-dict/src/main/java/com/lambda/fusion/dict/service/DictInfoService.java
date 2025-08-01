package com.lambda.fusion.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.dict.entity.DictInfo;
import com.lambda.fusion.dict.vo.DictTypeVo;
import java.util.List;
import java.util.Map;

/**
 * 多级数据字典详细信息
 *
 * @author Jin
 */
public interface DictInfoService extends IService<DictInfo> {

    /**
     * 分页查询
     *
     * @param pageable
     * @param parameters
     * @return
     */
    Page<DictInfo> page(Page<DictInfo> pageable, Map<String, Object> parameters);

    /**
     * 条件查询
     *
     * @param parameters
     * @return
     */
    List<DictInfo> selectDictInfo(Map<String, Object> parameters);

    /**
     * 新增数据字典详细信息
     *
     * @param operator
     * @param dictInfo
     * @return
     */
    DictInfo saveDictInfo(LoginUser operator, DictInfo dictInfo);

    /**
     * 更新数据字典详细信息
     *
     * @param dictInfo
     * @return
     */
    DictInfo updateDictInfo(DictInfo dictInfo);

    /**
     * 启用/禁用字典
     *
     * @param state
     * @param id
     */
    void changeState(int state, String id);

    /**
     * 设置可选/不可选择
     *
     * @param state
     * @param id
     */
    void changeSelectable(int state, String id);

    /**
     * 获取所有已启用的静态字典
     *
     * @param type 字典类型
     * @return
     */
    Map<String, Object> getStaticDictInfoGroup(String type);

    /**
     * 获取所有启用的动态字典
     *
     * @param type
     * @return
     */
    Map<String, DictTypeVo> getDynamicDictInfoGroup(String type);

    /**
     * 根据字典类型获取树型结构数据项
     *
     * @param type
     * @return
     */
    List<DictInfo> treeData(String type);

    /**
     * 根据数据类型查询包含子集数据类型的数据项
     *
     * @param type
     * @return
     */
    List<DictInfo> subTreeData(String type);

    /**
     * 根据数据项父ID查询数据项
     *
     * @param parentId
     * @return
     */
    List<DictInfo> queryDictInfoByParentId(String parentId);

    /**
     * 删除字典详细信息
     *
     * @param id
     */
    void deleteDictInfoById(String id);
}
