package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.core.Constants.JOINER;
import static com.lambda.fusion.dict.support.constants.DictConstants.*;

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
import com.lambda.fusion.core.identity.Operator;
import com.lambda.fusion.core.service.AbstractCrudService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.ParameterUtils;
import com.lambda.fusion.dict.mapper.DictInfoMapper;
import com.lambda.fusion.dict.mapper.DictTypeMapper;
import com.lambda.fusion.dict.model.DictInfoEntity;
import com.lambda.fusion.dict.model.DictInfoGroup;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.DictionaryEntry;
import com.lambda.fusion.dict.model.DictionaryType;
import com.lambda.fusion.dict.model.InputDictInfo;
import com.lambda.fusion.dict.model.OperationDictState;
import com.lambda.fusion.dict.model.QueryDictInfo;
import com.lambda.fusion.dict.support.enums.DictionaryRegistry;
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
public class DictInfoService extends AbstractCrudService<DictionaryEntry, InputDictInfo, DictInfoMapper>
        implements com.lambda.fusion.dict.service.DictInfoService, IService<DictionaryEntry> {

    private final Gson gson;

    private final DictInfoMapper dictInfoMapper;

    private final DictTypeMapper dictTypeMapper;

    @Override
    public Page<DictionaryEntry> page(Page<DictionaryEntry> pageable, QueryDictInfo queryDTO) {
        Map<String, Object> parameters = convertQueryDTOToMap(queryDTO);
        LambdaQueryWrapper<DictionaryEntry> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery.eq(DictionaryEntry::getTenantId, parameters.get(FIELD_TENANT_ID));
        lambdaQuery.eq(
                StrUtil.isNotEmpty(queryDTO.getDictType()), DictionaryEntry::getDictType, queryDTO.getDictType());
        lambdaQuery.like(
                StrUtil.isNotEmpty(queryDTO.getFieldType()), DictionaryEntry::getFieldType, queryDTO.getFieldType());
        lambdaQuery.like(
                StrUtil.isNotEmpty(queryDTO.getFieldName()), DictionaryEntry::getFieldName, queryDTO.getFieldName());
        lambdaQuery.eq(queryDTO.getEnableState() != null, DictionaryEntry::getEnableState, queryDTO.getEnableState());
        lambdaQuery.eq(
                StrUtil.isNotEmpty(queryDTO.getDictInfoId()), DictionaryEntry::getParentId, queryDTO.getDictInfoId());
        lambdaQuery
                .orderByAsc(DictionaryEntry::getDictType, DictionaryEntry::getSort)
                .orderByDesc(DictionaryEntry::getId);
        pageable = dictInfoMapper.page(pageable, parameters);
        pageable.getRecords().forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return pageable;
    }

    @Override
    public List<DictionaryEntry> selectDictInfo(QueryDictInfo queryDTO) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        LambdaQueryWrapper<DictionaryEntry> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery
                .eq(StrUtil.isNotEmpty(tenantId), DictionaryEntry::getTenantId, tenantId)
                .eq(StrUtil.isNotEmpty(queryDTO.getDictType()), DictionaryEntry::getDictType, queryDTO.getDictType())
                .like(
                        StrUtil.isNotEmpty(queryDTO.getFieldType()),
                        DictionaryEntry::getFieldType,
                        queryDTO.getFieldType())
                .like(
                        StrUtil.isNotEmpty(queryDTO.getFieldName()),
                        DictionaryEntry::getFieldName,
                        queryDTO.getFieldName())
                .eq(queryDTO.getEnableState() != null, DictionaryEntry::getEnableState, queryDTO.getEnableState())
                .eq(
                        StrUtil.isNotEmpty(queryDTO.getDictInfoId()),
                        DictionaryEntry::getParentId,
                        queryDTO.getDictInfoId())
                .orderByAsc(DictionaryEntry::getDictType, DictionaryEntry::getSort)
                .orderByDesc(DictionaryEntry::getId);
        List<DictionaryEntry> outcomes = dictInfoMapper.selectDictInfo(lambdaQuery);
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
    public DictionaryEntry saveDictInfo(LoginUser operator, DictionaryEntry source) {
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
            DictionaryEntry parent = dictInfoMapper.selectById(source.getParentId());
            if (null != parent) {
                source.setParentKeys(parent.buildParentKeys());
                source.setLevel(parent.getLevel() + 1);
            }
        } else {
            source.setLevel(DEFAULT_LEVEL);
        }
        dictInfoMapper.insert(source);
        DictionaryEntry dictionaryEntry = dictInfoMapper.selectById(source.getId());
        dictionaryEntry.setParameters(convertMap(dictionaryEntry.getExtra()));
        return dictionaryEntry;
    }

    public boolean dictInfoExists(DictionaryEntry dictionaryEntry) {
        LambdaQueryWrapper<DictionaryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictionaryEntry::getDictType, dictionaryEntry.getDictType());
        wrapper.eq(DictionaryEntry::getFieldType, dictionaryEntry.getFieldType());
        // 租户
        if (StringUtils.isNotBlank(dictionaryEntry.getTenantId())) {
            wrapper.eq(DictionaryEntry::getTenantId, dictionaryEntry.getTenantId());
        }
        DictionaryEntry target = dictInfoMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictionaryEntry.getId());
    }

    @Override
    public DictionaryEntry updateDictInfo(String id, DictInfoEntity dictInfoEntity) {
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
    public void updateEnableState(OperationDictState operationDTO) {
        DictionaryEntry parameter = new DictionaryEntry();
        parameter.setId(operationDTO.getId());
        parameter.setEnableState(operationDTO.getState());
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public void updateSelectableState(OperationDictState operationDTO) {
        DictionaryEntry parameter = new DictionaryEntry();
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
    public Map<String, DictionaryType> getDynamicDictInfoGroup(String dictType) {
        final LambdaQueryWrapper<DictTypeTree> query = Wrappers.lambdaQuery(DictTypeTree.class);
        if (StringUtils.isNotEmpty(dictType)) {
            query.like(DictTypeTree::getDictType, dictType);
        }
        final List<DictTypeTree> dictTypeTrees = dictTypeMapper.selectList(query);
        Map<String, DictionaryType> result = Maps.newHashMapWithExpectedSize(dictTypeTrees.size());

        // Enum List
        final List<DictTypeTree> enumList = DictionaryRegistry.getDictTypeList();
        if (CollectionUtils.isNotEmpty(enumList)) {
            dictTypeTrees.addAll(enumList);
        }

        for (DictTypeTree dictTypeTreeItem : dictTypeTrees) {
            final DictionaryType vo = BeanUtil.copyProperties(dictTypeTreeItem, DictionaryType.class);
            result.put(vo.getDictType(), vo);
        }
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(dictType) ? ParameterUtils.fuzzyQuery(dictType) : dictType, tenantId);
        for (DictInfoGroup info : lists) {
            if (result.containsKey(info.getDictType())) {
                final DictionaryType vo = result.get(info.getDictType());
                vo.setData(info.getDictList());
            }
        }
        return result;
    }

    @Override
    public List<DictionaryEntry> getTreeData(String dictType) {
        if (StringUtils.isBlank(dictType)) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        LambdaQueryWrapper<DictTypeTree> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTypeTree::getDictType, dictType);
        DictTypeTree dictTypeTreeEntity = dictTypeMapper.selectOne(wrapper);
        if (dictTypeTreeEntity == null) {
            return Collections.emptyList();
        }
        String currentKey = dictTypeTreeEntity.getParentKeys();
        if (StringUtils.isNotBlank(currentKey)) {
            if (currentKey.contains(JOINER)) {
                ids = Arrays.asList(currentKey.split(JOINER));
            } else {
                ids.add(currentKey);
            }
        } else {
            ids.add(dictTypeTreeEntity.getId());
        }
        Operator operator = ((Operator) OperatorUtils.getOperator());
        List<DictionaryEntry> outcomes = dictInfoMapper.treeList(ids, operator.getTenantId());
        return TreeBuilder.build(outcomes);
    }

    @Override
    public List<DictionaryEntry> getSubTreeData(String dictType) {
        List<DictionaryEntry> outcomes = new ArrayList<>();
        if (StringUtils.isNotBlank(dictType)) {
            Operator operator = ((Operator) OperatorUtils.getOperator());
            LambdaQueryWrapper<DictTypeTree> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DictTypeTree::getDictType, dictType);
            DictTypeTree dictTypeTreeEntity = dictTypeMapper.selectOne(wrapper);
            String currentKey = dictTypeTreeEntity.getId();
            LambdaQueryWrapper<DictTypeTree> conditions = new LambdaQueryWrapper<>();
            conditions.likeRight(DictTypeTree::getParentKeys, currentKey).or().eq(DictTypeTree::getId, currentKey);
            List<DictTypeTree> dictTypeTrees = dictTypeMapper.selectList(conditions);
            if (CollectionUtils.isNotEmpty(dictTypeTrees)) {
                List<String> ids = dictTypeTrees.stream().map(DictTypeTree::id).collect(Collectors.toList());
                List<DictionaryEntry> list = dictInfoMapper.treeList(ids, operator.getTenantId());
                list.forEach(info -> info.setParameters(
                        StringUtils.isNotBlank(info.getExtra()) ? convertMap(info.getExtra()) : null));
                outcomes = TreeBuilder.build(list);
            }
        }
        outcomes.sort(Comparator.comparing(DictionaryEntry::getSort));
        return outcomes;
    }

    @Override
    public List<DictionaryEntry> getDictInfoByParentId(String parentId) {
        DictionaryEntry dictionaryEntry = dictInfoMapper.selectById(parentId);
        if (Objects.isNull(dictionaryEntry)) {
            return Collections.emptyList();
        }
        return TreeBuilder.build(queryParentDictInfo(dictionaryEntry));
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

    private List<DictionaryEntry> queryParentDictInfo(DictionaryEntry dictionaryEntry) {
        DictionaryEntry wrapper = new DictionaryEntry();
        String keys = dictionaryEntry.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            wrapper.setParentId(ParameterUtils.fuzzyQuery(keys.substring(keys.length() - PARENT_KEY_SUBSTRING_LENGTH)));
        } else {
            wrapper.setId(dictionaryEntry.getId());
        }
        wrapper.setLevel(dictionaryEntry.getLevel());
        wrapper.setDictType(dictionaryEntry.getDictType());
        Operator operator = ((Operator) OperatorUtils.getOperator());
        wrapper.setTenantId(operator.getTenantId());

        List<DictionaryEntry> target = dictInfoMapper.getDictInfoList(wrapper);
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

    private Map<String, Object> convertQueryDTOToMap(QueryDictInfo queryDTO) {
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
