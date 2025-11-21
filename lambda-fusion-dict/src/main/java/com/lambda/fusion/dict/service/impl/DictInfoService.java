package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.core.Constants.JOINER;
import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.service.AbstractCrudService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.user.Operator;
import com.lambda.fusion.core.utils.ParameterUtils;
import com.lambda.fusion.dict.common.enums.DictContextHolders;
import com.lambda.fusion.dict.mapper.DictInfoMapper;
import com.lambda.fusion.dict.mapper.DictTypeMapper;
import com.lambda.fusion.dict.model.dto.DictInfoInputDTO;
import com.lambda.fusion.dict.model.dto.DictInfoQueryDTO;
import com.lambda.fusion.dict.model.dto.DictStateOperationDTO;
import com.lambda.fusion.dict.model.entity.DictInfoEntity;
import com.lambda.fusion.dict.model.entity.DictInfoGroup;
import com.lambda.fusion.dict.model.entity.DictType;
import com.lambda.fusion.dict.model.vo.DictInfoVO;
import com.lambda.fusion.dict.model.vo.DictTypeVO;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多级数据字典详细信息
 *
 * @author Jin
 */
@RequiredArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class DictInfoService extends AbstractCrudService<DictInfoVO, DictInfoInputDTO, DictInfoMapper>
        implements com.lambda.fusion.dict.service.DictInfoService, IService<DictInfoVO> {

    private final Gson gson;

    private final DictInfoMapper dictInfoMapper;

    private final DictTypeMapper dictTypeMapper;

    @Override
    public Page<DictInfoVO> page(Page<DictInfoVO> pageable, DictInfoQueryDTO queryDTO) {
        Map<String, Object> parameters = convertQueryDTOToMap(queryDTO);
        LambdaQueryWrapper<DictInfoVO> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery.eq(DictInfoVO::getTenantId, parameters.get(FIELD_TENANT_ID));
        lambdaQuery.eq(StrUtil.isNotEmpty(queryDTO.getDictType()), DictInfoVO::getDictType, queryDTO.getDictType());
        lambdaQuery.like(
                StrUtil.isNotEmpty(queryDTO.getFieldType()), DictInfoVO::getFieldType, queryDTO.getFieldType());
        lambdaQuery.like(
                StrUtil.isNotEmpty(queryDTO.getFieldName()), DictInfoVO::getFieldName, queryDTO.getFieldName());
        lambdaQuery.eq(queryDTO.getEnableState() != null, DictInfoVO::getEnableState, queryDTO.getEnableState());
        lambdaQuery.eq(StrUtil.isNotEmpty(queryDTO.getDictInfoId()), DictInfoVO::getParentId, queryDTO.getDictInfoId());
        lambdaQuery.orderByAsc(DictInfoVO::getDictType, DictInfoVO::getSort).orderByDesc(DictInfoVO::getId);
        pageable = dictInfoMapper.page(pageable, parameters);
        pageable.getRecords().forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return pageable;
    }

    @Override
    public List<DictInfoVO> selectDictInfo(DictInfoQueryDTO queryDTO) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        LambdaQueryWrapper<DictInfoVO> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery
                .eq(StrUtil.isNotEmpty(tenantId), DictInfoVO::getTenantId, tenantId)
                .eq(StrUtil.isNotEmpty(queryDTO.getDictType()), DictInfoVO::getDictType, queryDTO.getDictType())
                .like(StrUtil.isNotEmpty(queryDTO.getFieldType()), DictInfoVO::getFieldType, queryDTO.getFieldType())
                .like(StrUtil.isNotEmpty(queryDTO.getFieldName()), DictInfoVO::getFieldName, queryDTO.getFieldName())
                .eq(queryDTO.getEnableState() != null, DictInfoVO::getEnableState, queryDTO.getEnableState())
                .eq(StrUtil.isNotEmpty(queryDTO.getDictInfoId()), DictInfoVO::getParentId, queryDTO.getDictInfoId())
                .orderByAsc(DictInfoVO::getDictType, DictInfoVO::getSort)
                .orderByDesc(DictInfoVO::getId);
        List<DictInfoVO> outcomes = dictInfoMapper.selectDictInfo(lambdaQuery);
        if (CollectionUtils.isEmpty(outcomes)) {
            return Collections.emptyList();
        }
        outcomes.forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return TreeBuilder.build(outcomes);
    }

    @Override
    public DictInfoVO saveDictInfo(LoginUser operator, DictInfoVO source) {
        String dictType = source.getDictType();
        String fieldType = source.getFieldType();
        String fieldName = source.getFieldName();
        Assert.notNull(source, "");
        Assert.hasText(dictType, MSG_DICT_TYPE_NOT_EMPTY);
        Assert.hasText(fieldType, MSG_DICT_FIELD_TYPE_NOT_EMPTY);
        Assert.hasText(fieldName, MSG_DICT_FIELD_NAME_NOT_EMPTY);
        Assert.notNull(source.getSort(), MSG_DICT_SORT_NUMBER_NOT_EMPTY);
        Assert.notNull(source.getEnableState(), MSG_DICT_ENABLED_NOT_EMPTY);
        source.setExtra(
                CollectionUtils.isNotEmpty(source.getParameters()) ? convertJson(source.getParameters()) : null);
        if (StringUtils.isNotBlank(source.getParentId())) {
            DictInfoVO parent = dictInfoMapper.selectById(source.getParentId());
            if (null != parent) {
                source.setParentKeys(parent.buildParentKeys());
                source.setLevel(parent.getLevel() + 1);
            }
        } else {
            source.setLevel(DEFAULT_LEVEL);
        }
        dictInfoMapper.insert(source);
        DictInfoVO dictInfoVO = dictInfoMapper.selectById(source.getId());
        dictInfoVO.setParameters(convertMap(dictInfoVO.getExtra()));
        return dictInfoVO;
    }

    public boolean dictInfoExists(DictInfoVO dictInfoVO) {
        LambdaQueryWrapper<DictInfoVO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictInfoVO::getDictType, dictInfoVO.getDictType());
        wrapper.eq(DictInfoVO::getFieldType, dictInfoVO.getFieldType());
        // 租户
        if (StringUtils.isNotBlank(dictInfoVO.getTenantId())) {
            wrapper.eq(DictInfoVO::getTenantId, dictInfoVO.getTenantId());
        }
        DictInfoVO target = dictInfoMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictInfoVO.getId());
    }

    @Override
    public DictInfoVO updateDictInfo(String id, DictInfoEntity dictInfoEntity) {
        Assert.notNull(id, MSG_DICT_ID_NOT_EMPTY);
        Assert.notNull(dictInfoMapper.selectById(id), MSG_DICT_UPDATE_DATA_NOT_EXISTED);

        //        dictInfoVO.setExtra(
        //                CollectionUtils.isNotEmpty(dictInfoVO.getParameters())
        //                        ? convertJson(dictInfoVO.getParameters())
        //                        : null);
        //        if (dictInfoExists(dictInfoVO)) {
        //            dictInfoVO.setFieldType(null);
        //        }
        //        dictInfoMapper.updateById(dictInfoEntity);
        return dictInfoMapper.selectById(id);
    }

    @Override
    public void updateEnableState(DictStateOperationDTO operationDTO) {
        DictInfoVO parameter = new DictInfoVO();
        parameter.setId(operationDTO.getId());
        parameter.setEnableState(operationDTO.getState());
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public void updateSelectableState(DictStateOperationDTO operationDTO) {
        DictInfoVO parameter = new DictInfoVO();
        parameter.setId(operationDTO.getId());
        parameter.setSelectable(operationDTO.getState());
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public Map<String, Object> getStaticDictInfoGroup(String dictType) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(dictType) ? ParameterUtils.fuzzyQuery(dictType) : dictType, tenantId);
        Map<String, Object> map = Maps.newHashMapWithExpectedSize(lists.size());
        for (DictInfoGroup info : lists) {
            map.put(info.getDictType(), info.getDictList());
        }
        return map;
    }

    @Override
    public Map<String, DictTypeVO> getDynamicDictInfoGroup(String dictType) {
        final LambdaQueryWrapper<DictType> query = Wrappers.lambdaQuery(DictType.class);
        if (StringUtils.isNotEmpty(dictType)) {
            query.like(DictType::getDictType, dictType);
        }
        final List<DictType> dictTypes = dictTypeMapper.selectList(query);
        Map<String, DictTypeVO> result = Maps.newHashMapWithExpectedSize(dictTypes.size());

        // Enum List
        final List<DictType> enumList = DictContextHolders.getDictTypeList();
        if (CollectionUtils.isNotEmpty(enumList)) {
            dictTypes.addAll(enumList);
        }

        for (DictType dictTypeItem : dictTypes) {
            final DictTypeVO vo = BeanUtil.copyProperties(dictTypeItem, DictTypeVO.class);
            result.put(vo.getDictType(), vo);
        }
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(dictType) ? ParameterUtils.fuzzyQuery(dictType) : dictType, tenantId);
        for (DictInfoGroup info : lists) {
            if (result.containsKey(info.getDictType())) {
                final DictTypeVO vo = result.get(info.getDictType());
                vo.setData(info.getDictList());
            }
        }
        return result;
    }

    @Override
    public List<DictInfoVO> getTreeData(String dictType) {
        if (StringUtils.isBlank(dictType)) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getDictType, dictType);
        DictType dictTypeEntity = dictTypeMapper.selectOne(wrapper);
        if (dictTypeEntity == null) {
            return Collections.emptyList();
        }
        String currentKey = dictTypeEntity.getParentKeys();
        if (StringUtils.isNotBlank(currentKey)) {
            if (currentKey.contains(JOINER)) {
                ids = Arrays.asList(currentKey.split(JOINER));
            } else {
                ids.add(currentKey);
            }
        } else {
            ids.add(dictTypeEntity.getId());
        }
        Operator operator = ((Operator) OperatorUtils.getOperator());
        List<DictInfoVO> outcomes = dictInfoMapper.treeList(ids, operator.getTenantId());
        return TreeBuilder.build(outcomes);
    }

    @Override
    public List<DictInfoVO> getSubTreeData(String dictType) {
        List<DictInfoVO> outcomes = new ArrayList<>();
        if (StringUtils.isNotBlank(dictType)) {
            Operator operator = ((Operator) OperatorUtils.getOperator());
            LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DictType::getDictType, dictType);
            DictType dictTypeEntity = dictTypeMapper.selectOne(wrapper);
            String currentKey = dictTypeEntity.getId();
            LambdaQueryWrapper<DictType> conditions = new LambdaQueryWrapper<>();
            conditions.likeRight(DictType::getParentKeys, currentKey).or().eq(DictType::getId, currentKey);
            List<DictType> dictTypes = dictTypeMapper.selectList(conditions);
            if (CollectionUtils.isNotEmpty(dictTypes)) {
                List<String> ids = dictTypes.stream().map(DictType::id).collect(Collectors.toList());
                List<DictInfoVO> list = dictInfoMapper.treeList(ids, operator.getTenantId());
                list.forEach(info -> info.setParameters(
                        StringUtils.isNotBlank(info.getExtra()) ? convertMap(info.getExtra()) : null));
                outcomes = TreeBuilder.build(list);
            }
        }
        outcomes.sort(Comparator.comparing(DictInfoVO::getSort));
        return outcomes;
    }

    @Override
    public List<DictInfoVO> getDictInfoByParentId(String parentId) {
        DictInfoVO dictInfoVO = dictInfoMapper.selectById(parentId);
        if (Objects.isNull(dictInfoVO)) {
            return Collections.emptyList();
        }
        return TreeBuilder.build(queryParentDictInfo(dictInfoVO));
    }

    @Override
    public boolean deleteDictInfoById(String id) {
        try {
            dictInfoMapper.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<DictInfoVO> queryParentDictInfo(DictInfoVO dictInfoVO) {
        DictInfoVO wrapper = new DictInfoVO();
        String keys = dictInfoVO.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            wrapper.setParentId(ParameterUtils.fuzzyQuery(keys.substring(keys.length() - PARENT_KEY_SUBSTRING_LENGTH)));
        } else {
            wrapper.setId(dictInfoVO.getId());
        }
        wrapper.setLevel(dictInfoVO.getLevel());
        wrapper.setDictType(dictInfoVO.getDictType());
        Operator operator = ((Operator) OperatorUtils.getOperator());
        wrapper.setTenantId(operator.getTenantId());

        List<DictInfoVO> target = dictInfoMapper.getDictInfoList(wrapper);
        target.forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return target;
    }

    private String convertJson(Map<String, Object> map) {
        return gson.toJson(map);
    }

    private Map<String, Object> convertMap(String extra) {
        return gson.fromJson(extra, new MapTypeToken().getType());
    }

    private Map<String, Object> convertQueryDTOToMap(DictInfoQueryDTO queryDTO) {
        if (queryDTO == null) {
            return new HashMap<>();
        }
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(8);
        if (StringUtils.isNotBlank(queryDTO.getDictType())) {
            parameters.put(FIELD_DICT_TYPE, queryDTO.getDictType());
        }
        if (StringUtils.isNotBlank(queryDTO.getFieldType())) {
            parameters.put(FIELD_FIELD_TYPE, ParameterUtils.fuzzyQuery(queryDTO.getFieldType()));
        }
        if (StringUtils.isNotBlank(queryDTO.getFieldName())) {
            parameters.put(FIELD_FIELD_NAME, ParameterUtils.fuzzyQuery(queryDTO.getFieldName()));
        }
        if (StringUtils.isNotBlank(queryDTO.getParentId())) {
            parameters.put(FIELD_PARENT_ID, queryDTO.getParentId());
        }
        if (queryDTO.getEnableState() != null) {
            parameters.put(FIELD_ENABLE_STATE, queryDTO.getEnableState());
        }
        if (queryDTO.getDictInfoId() != null) {
            parameters.put(FIELD_DICT_INFO_ID, queryDTO.getDictInfoId());
        }
        if (queryDTO.getSelectable() != null) {
            parameters.put(FIELD_SELECTABLE, queryDTO.getSelectable());
        }
        Operator operator = (Operator) OperatorUtils.getOperator();
        parameters.put(FIELD_TENANT_ID, operator.getTenantId());

        if (queryDTO.getExtraParams() != null && !queryDTO.getExtraParams().isEmpty()) {
            parameters.putAll(queryDTO.getExtraParams());
        }
        return parameters;
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {}
}
