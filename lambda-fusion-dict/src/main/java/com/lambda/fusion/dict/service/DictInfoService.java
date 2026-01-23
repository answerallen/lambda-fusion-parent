package com.lambda.fusion.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictType;
import com.lambda.fusion.dict.model.OperationDictState;
import com.lambda.fusion.dict.model.QueryDictInfo;
import java.util.List;
import java.util.Map;

/**
 * 多级数据字典详细信息
 *
 * @author Jin
 */
public interface DictInfoService extends IService<DictInfo> {

    /**
     * 分页查询字典信息
     *
     * @param pageable 分页参数
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    Page<DictInfo> page(Page<DictInfo> pageable, QueryDictInfo queryDTO);

    /**
     * 根据条件查询字典信息列表
     *
     * @param queryDTO 查询条件
     * @return 字典信息列表
     */
    List<DictInfo> selectDictInfo(QueryDictInfo queryDTO);

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
     * @param id
     * @param dictInfo
     */
    void updateDictInfo(String id, DictInfo dictInfo);

    /**
     * 修改字典启用状态
     *
     * @param operationDTO 状态操作参数
     */
    void updateEnableState(OperationDictState operationDTO);

    /**
     * 修改字典可选择状态
     *
     * @param operationDTO 状态操作参数
     */
    void updateSelectableState(OperationDictState operationDTO);

    /**
     * 获取已启用的静态字典分组
     *
     * @param dictType 字典类型，支持模糊查询
     * @return 静态字典分组数据
     */
    Map<String, Object> getStaticDictInfoGroup(String dictType);

    /**
     * 获取已启用的动态字典分组
     *
     * @param dictType 字典类型，支持模糊查询
     * @return 动态字典分组数据
     */
    Map<String, DictType> getDynamicDictInfoGroup(String dictType);

    /**
     * 根据字典类型获取树型结构数据
     *
     * @param dictType 字典类型
     * @return 树型结构的字典数据
     */
    List<DictInfo> getTreeData(String dictType);

    /**
     * 根据字典类型查询包含子集的树型数据
     *
     * @param dictType 字典类型
     * @return 包含子集的树型字典数据
     */
    List<DictInfo> getSubTreeData(String dictType);

    /**
     * 根据父级ID查询子级字典数据
     *
     * @param parentId 父级ID
     * @return 子级字典数据列表
     */
    List<DictInfo> getDictInfoByParentId(String parentId);

    /**
     * 根据ID删除字典信息
     *
     * @param id 字典ID
     * @return 是否删除成功
     */
    boolean deleteDictInfoById(String id);
}
