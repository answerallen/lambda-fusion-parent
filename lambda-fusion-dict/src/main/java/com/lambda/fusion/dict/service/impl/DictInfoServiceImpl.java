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
import com.lambda.fusion.core.identity.UserPrincipal;
import com.lambda.fusion.core.service.AbstractCrudService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.ParameterUtils;
import com.lambda.fusion.dict.mapper.DictInfoMapper;
import com.lambda.fusion.dict.mapper.DictTypeMapper;
import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictInfoEntity;
import com.lambda.fusion.dict.model.DictInfoGroup;
import com.lambda.fusion.dict.model.DictType;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.InputDictInfo;
import com.lambda.fusion.dict.model.OperationDictState;
import com.lambda.fusion.dict.model.QueryDictInfo;
import com.lambda.fusion.dict.service.DictInfoService;
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
public class DictInfoServiceImpl extends AbstractCrudService<DictInfo, InputDictInfo, DictInfoMapper>
        implements DictInfoService, IService<DictInfo> {

    private final Gson gson;

    private final DictInfoMapper dictInfoMapper;

    private final DictTypeMapper dictTypeMapper;

    @Override
    public Page<DictInfo> page(Page<DictInfo> pageable, QueryDictInfo queryDTO) {
        Map<String, Object> parameters = convertQueryDTOToMap(queryDTO);
        LambdaQueryWrapper<DictInfo> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery.eq(DictInfo::getTenantId, parameters.get(FIELD_TENANT_ID));
        lambdaQuery.eq(StrUtil.isNotEmpty(queryDTO.getDictType()), DictInfo::getDictType, queryDTO.getDictType());
        lambdaQuery.like(StrUtil.isNotEmpty(queryDTO.getFieldType()), DictInfo::getFieldType, queryDTO.getFieldType());
        lambdaQuery.like(StrUtil.isNotEmpty(queryDTO.getFieldName()), DictInfo::getFieldName, queryDTO.getFieldName());
        lambdaQuery.eq(queryDTO.getEnableState() != null, DictInfo::getEnableState, queryDTO.getEnableState());
        lambdaQuery.eq(StrUtil.isNotEmpty(queryDTO.getDictInfoId()), DictInfo::getParentId, queryDTO.getDictInfoId());
        lambdaQuery.orderByAsc(DictInfo::getDictType, DictInfo::getSort).orderByDesc(DictInfo::getId);
        pageable = dictInfoMapper.page(pageable, parameters);
        pageable.getRecords().forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return pageable;
    }

    @Override
    public List<DictInfo> selectDictInfo(QueryDictInfo queryDTO) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        LambdaQueryWrapper<DictInfo> lambdaQuery = Wrappers.lambdaQuery();
        lambdaQuery
                .eq(StrUtil.isNotEmpty(tenantId), DictInfo::getTenantId, tenantId)
                .eq(StrUtil.isNotEmpty(queryDTO.getDictType()), DictInfo::getDictType, queryDTO.getDictType())
                .like(StrUtil.isNotEmpty(queryDTO.getFieldType()), DictInfo::getFieldType, queryDTO.getFieldType())
                .like(StrUtil.isNotEmpty(queryDTO.getFieldName()), DictInfo::getFieldName, queryDTO.getFieldName())
                .eq(queryDTO.getEnableState() != null, DictInfo::getEnableState, queryDTO.getEnableState())
                .eq(StrUtil.isNotEmpty(queryDTO.getDictInfoId()), DictInfo::getParentId, queryDTO.getDictInfoId())
                .orderByAsc(DictInfo::getDictType, DictInfo::getSort)
                .orderByDesc(DictInfo::getId);
        List<DictInfo> outcomes = dictInfoMapper.selectDictInfo(lambdaQuery);
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
    public DictInfo saveDictInfo(LoginUser operator, DictInfo source) {
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
            DictInfo parent = dictInfoMapper.selectById(source.getParentId());
            if (null != parent) {
                source.setParentKeys(parent.buildParentKeys());
                source.setLevel(parent.getLevel() + 1);
            }
        } else {
            source.setLevel(DEFAULT_LEVEL);
        }
        dictInfoMapper.insert(source);
        DictInfo dictionaryEntry = dictInfoMapper.selectById(source.getId());
        dictionaryEntry.setParameters(convertMap(dictionaryEntry.getExtra()));
        return dictionaryEntry;
    }

    public boolean dictInfoExists(DictInfo dictionaryEntry) {
        LambdaQueryWrapper<DictInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictInfo::getDictType, dictionaryEntry.getDictType());
        wrapper.eq(DictInfo::getFieldType, dictionaryEntry.getFieldType());
        // 租户
        if (StringUtils.isNotBlank(dictionaryEntry.getTenantId())) {
            wrapper.eq(DictInfo::getTenantId, dictionaryEntry.getTenantId());
        }
        DictInfo target = dictInfoMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictionaryEntry.getId());
    }

    @Override
    public DictInfo updateDictInfo(String id, DictInfoEntity dictInfoEntity) {
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
        DictInfo parameter = new DictInfo();
        parameter.setId(operationDTO.getId());
        parameter.setEnableState(operationDTO.getState());
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public void updateSelectableState(OperationDictState operationDTO) {
        DictInfo parameter = new DictInfo();
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
    public Map<String, DictType> getDynamicDictInfoGroup(String dictType) {
        final LambdaQueryWrapper<DictTypeTree> query = Wrappers.lambdaQuery(DictTypeTree.class);
        if (StringUtils.isNotEmpty(dictType)) {
            query.like(DictTypeTree::getDictType, dictType);
        }
        final List<DictTypeTree> dictTypeTrees = dictTypeMapper.selectList(query);
        Map<String, DictType> result = Maps.newHashMapWithExpectedSize(dictTypeTrees.size());

        // Enum List
        final List<DictTypeTree> enumList = DictionaryRegistry.getDictTypeList();
        if (CollectionUtils.isNotEmpty(enumList)) {
            dictTypeTrees.addAll(enumList);
        }

        for (DictTypeTree dictTypeTreeItem : dictTypeTrees) {
            final DictType vo = BeanUtil.copyProperties(dictTypeTreeItem, DictType.class);
            result.put(vo.getDictType(), vo);
        }
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(dictType) ? ParameterUtils.fuzzyQuery(dictType) : dictType, tenantId);
        for (DictInfoGroup info : lists) {
            if (result.containsKey(info.getDictType())) {
                final DictType vo = result.get(info.getDictType());
                vo.setData(info.getDictList());
            }
        }
        return result;
    }

    @Override
    public List<DictInfo> getTreeData(String dictType) {
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
        UserPrincipal userPrincipal = ((UserPrincipal) OperatorUtils.getOperator());
        List<DictInfo> outcomes = dictInfoMapper.treeList(ids, userPrincipal.getTenantId());
        return TreeBuilder.build(outcomes);
    }

    @Override
    public List<DictInfo> getSubTreeData(String dictType) {
        List<DictInfo> outcomes = new ArrayList<>();
        if (StringUtils.isNotBlank(dictType)) {
            UserPrincipal userPrincipal = ((UserPrincipal) OperatorUtils.getOperator());
            LambdaQueryWrapper<DictTypeTree> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DictTypeTree::getDictType, dictType);
            DictTypeTree dictTypeTreeEntity = dictTypeMapper.selectOne(wrapper);
            String currentKey = dictTypeTreeEntity.getId();
            LambdaQueryWrapper<DictTypeTree> conditions = new LambdaQueryWrapper<>();
            conditions.likeRight(DictTypeTree::getParentKeys, currentKey).or().eq(DictTypeTree::getId, currentKey);
            List<DictTypeTree> dictTypeTrees = dictTypeMapper.selectList(conditions);
            if (CollectionUtils.isNotEmpty(dictTypeTrees)) {
                List<String> ids = dictTypeTrees.stream().map(DictTypeTree::id).collect(Collectors.toList());
                List<DictInfo> list = dictInfoMapper.treeList(ids, userPrincipal.getTenantId());
                list.forEach(info -> info.setParameters(
                        StringUtils.isNotBlank(info.getExtra()) ? convertMap(info.getExtra()) : null));
                outcomes = TreeBuilder.build(list);
            }
        }
        outcomes.sort(Comparator.comparing(DictInfo::getSort));
        return outcomes;
    }

    @Override
    public List<DictInfo> getDictInfoByParentId(String parentId) {
        DictInfo dictionaryEntry = dictInfoMapper.selectById(parentId);
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

    private List<DictInfo> queryParentDictInfo(DictInfo dictionaryEntry) {
        DictInfo wrapper = new DictInfo();
        String keys = dictionaryEntry.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            wrapper.setParentId(ParameterUtils.fuzzyQuery(keys.substring(keys.length() - PARENT_KEY_SUBSTRING_LENGTH)));
        } else {
            wrapper.setId(dictionaryEntry.getId());
        }
        wrapper.setLevel(dictionaryEntry.getLevel());
        wrapper.setDictType(dictionaryEntry.getDictType());
        UserPrincipal userPrincipal = ((UserPrincipal) OperatorUtils.getOperator());
        wrapper.setTenantId(userPrincipal.getTenantId());

        List<DictInfo> target = dictInfoMapper.getDictInfoList(wrapper);
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
        UserPrincipal userPrincipal = (UserPrincipal) OperatorUtils.getOperator();
        parameters.put(FIELD_TENANT_ID, userPrincipal.getTenantId());

        if (queryDTO.getExtraParams() != null && !queryDTO.getExtraParams().isEmpty()) {
            parameters.putAll(queryDTO.getExtraParams());
        }
        return parameters;
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {}
}
