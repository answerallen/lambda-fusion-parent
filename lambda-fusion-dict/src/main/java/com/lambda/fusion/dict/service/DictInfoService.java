package com.lambda.fusion.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.dict.model.dto.DictInfoQueryDTO;
import com.lambda.fusion.dict.model.dto.DictStateOperationDTO;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import com.lambda.fusion.dict.model.vo.DictInfoVO;
import com.lambda.fusion.dict.model.vo.DictTypeVO;
import java.util.List;
import java.util.Map;

/**
 * 多级数据字典详细信息
 *
 * @author Jin
 */
public interface DictInfoService extends IService<DictInfoVO> {

    /**
     * 分页查询字典信息
     *
     * @param pageable 分页参数
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    Page<DictInfoVO> page(Page<DictInfoVO> pageable, DictInfoQueryDTO queryDTO);

    /**
     * 根据条件查询字典信息列表
     *
     * @param queryDTO 查询条件
     * @return 字典信息列表
     */
    List<DictInfoVO> selectDictInfo(DictInfoQueryDTO queryDTO);

    /**
     * 新增数据字典详细信息
     *
     * @param operator
     * @param dictInfoVO
     * @return
     */
    DictInfoVO saveDictInfo(LoginUser operator, DictInfoVO dictInfoVO);

    /**
     * 更新数据字典详细信息
     *
     * @param id
     * @param dictInfoVO
     * @return
     */
    DictInfoVO updateDictInfo(String id, DictInfoEntity dictInfoVO);

    /**
     * 修改字典启用状态
     *
     * @param operationDTO 状态操作参数
     */
    void updateEnableState(DictStateOperationDTO operationDTO);

    /**
     * 修改字典可选择状态
     *
     * @param operationDTO 状态操作参数
     */
    void updateSelectableState(DictStateOperationDTO operationDTO);

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
    Map<String, DictTypeVO> getDynamicDictInfoGroup(String dictType);

    /**
     * 根据字典类型获取树型结构数据
     *
     * @param dictType 字典类型
     * @return 树型结构的字典数据
     */
    List<DictInfoVO> getTreeData(String dictType);

    /**
     * 根据字典类型查询包含子集的树型数据
     *
     * @param dictType 字典类型
     * @return 包含子集的树型字典数据
     */
    List<DictInfoVO> getSubTreeData(String dictType);

    /**
     * 根据父级ID查询子级字典数据
     *
     * @param parentId 父级ID
     * @return 子级字典数据列表
     */
    List<DictInfoVO> getDictInfoByParentId(String parentId);

    /**
     * 根据ID删除字典信息
     *
     * @param id 字典ID
     * @return 是否删除成功
     */
    boolean deleteDictInfoById(String id);
}
