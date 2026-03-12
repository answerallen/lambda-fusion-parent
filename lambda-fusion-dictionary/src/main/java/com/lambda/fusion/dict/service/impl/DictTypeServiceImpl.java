package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.dict.DictConstants.*;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import com.lambda.fusion.core.utils.SqlParamUtils;
import com.lambda.fusion.dict.DictProperties;
import com.lambda.fusion.dict.mapper.DictInfoMapper;
import com.lambda.fusion.dict.mapper.DictTypeMapper;
import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.QueryDictTree;
import com.lambda.fusion.dict.service.DictTypeService;
import com.lambda.fusion.dict.support.DictValueType;
import com.lambda.fusion.dict.support.model.DynamicDictSource;
import com.lambda.fusion.dict.support.registry.DictRegistry;
import com.lambda.fusion.dict.support.resolve.DictSourceResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多级数据字典类型
 */
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DictTypeServiceImpl extends ServiceImpl<DictTypeMapper, DictTypeTree> implements DictTypeService {

    private final DictTypeMapper dictTypeMapper;

    private final DictInfoMapper dictInfoMapper;

    private final TreeDataFilter treeDataFilter;

    private final List<DictSourceResolver> dynamicDictResolves;

    private final DictProperties dictProperties;

    @Override
    public DictTypeTree saveDictType(DictTypeTree source) {
        source.setId(IdUtil.getSnowflakeNextIdStr());
        source.setDictType(Optional.ofNullable(source.getDictType()).orElse(source.getId()));
        Assert.notNull(source, MSG_DICT_TYPE_NOT_EMPTY);
        Assert.hasText(source.getDictName(), MSG_DICT_NAME_NOT_EMPTY);
        Assert.isFalse(dictTypeExists(source), MSG_DICT_TYPE_EXISTED);
        if (StringUtils.isNotBlank(source.getParentId())) {
            DictTypeTree parent = dictTypeMapper.selectById(source.getParentId());
            if (null != parent) {
                source.setParentKeys(parent.buildParentKeys());
                source.setLevel(parent.getLevel() + 1);
            }
        } else {
            source.setLevel(DEFAULT_LEVEL);
        }
        if (StringUtils.isBlank(source.getDictType())) {
            source.setDictType(source.getId());
        }
        source.setCreatedBy(OperatorUtils.getOperator().getName());
        source.setCreatedAt(LocalDateTime.now());
        dictTypeMapper.insert(source);
        return dictTypeMapper.selectById(source.getId());
    }

    @Override
    public void updateDictType(DictTypeTree source) {
        Assert.notNull(source.getId(), MSG_DICT_ID_NOT_EMPTY);
        Assert.hasText(source.getDictName(), MSG_DICT_NAME_NOT_EMPTY);
        Assert.isFalse(dictTypeExists(source), MSG_DICT_TYPE_EXISTED);
        dictTypeMapper.updateById(source);
    }

    public boolean dictTypeExists(DictTypeTree dictTypeTree) {
        LambdaQueryWrapper<DictTypeTree> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTypeTree::getDictType, dictTypeTree.getDictType())
                .or()
                .eq(DictTypeTree::getDictName, dictTypeTree.getDictName());
        DictTypeTree target = dictTypeMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictTypeTree.getId());
    }

    @SuppressWarnings("unused")
    public List<DictTypeTree> staticTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        if (StringUtils.isNotBlank(type)) {
            DictTypeTree conditions =
                    dictTypeMapper.selectOne(new QueryWrapper<DictTypeTree>().eq(FIELD_DICT_TYPE, type));
            Assert.notNull(conditions, MSG_DICT_TYPE_NOT_EXISTED);
            assemblyQueryParameter(parameters, conditions);
            parameters.put(FIELD_LEVEL, conditions.getLevel());
        }
        return TreeBuilder.build(dictTypeMapper.treeList(parameters));
    }

    @Override
    public List<DictTypeTree> compositeTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        String name = queryDictTree.getName();
        Integer dataType = queryDictTree.getDataType();
        DictValueType dictValueType = DictValueType.of(dataType);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        if (StringUtils.isNotBlank(name)) {
            parameters.put("name", SqlParamUtils.fuzzyQuery(name));
        }

        List<DictTypeTree> result = new ArrayList<>();
        if (dataType == null || dictValueType.isEnumDict()) {
            // 枚举字典
            if (StringUtils.isNotBlank(type)) {
                DictTypeTree conditions = dictTypeMapper.selectOne(
                        new LambdaQueryWrapper<DictTypeTree>().eq(DictTypeTree::getDictType, type));
                if (Objects.nonNull(conditions)) {
                    assemblyQueryParameter(parameters, conditions);
                }
                final DictTypeTree enumDictTypeTree = DictRegistry.getDictType(queryDictTree.getType());
                if (Objects.nonNull(enumDictTypeTree)) {
                    if (StringUtils.isNotBlank(name)) {
                        if (StringUtils.contains(enumDictTypeTree.getDictName(), name)) {
                            result.add(enumDictTypeTree);
                        }
                    } else {
                        result.add(enumDictTypeTree);
                    }
                }
            } else {
                final List<DictTypeTree> enumList = DictRegistry.getDictTypeList();
                if (CollectionUtils.isNotEmpty(enumList)) {
                    if (StringUtils.isNotBlank(name)) {
                        List<DictTypeTree> list = enumList.stream()
                                .filter(enumDict -> StringUtils.contains(enumDict.getDictName(), name))
                                .toList();
                        result.addAll(list);
                    } else {
                        result.addAll(enumList);
                    }
                }
            }
        }
        if (dataType == null || !dictValueType.isNotEnumDict()) {
            // 非枚举字典
            parameters.put(FIELD_DICT_TYPE, dataType);
            parameters.put("userOnly", queryDictTree.isUserOnly());
            List<DictTypeTree> dictTypeTrees = dictTypeMapper.treeList(parameters);
            result.addAll(dictTypeTrees);
        }
        final List<DictTypeTree> typeList = treeDataFilter.filter(
                result,
                queryDictTree.getType(),
                DictTypeTree::getDictType,
                DictTypeTree::getId,
                DictTypeTree::getParentKeys,
                target -> target.stream()
                        .sorted(Comparator.comparing(DictTypeTree::getDictName))
                        .collect(Collectors.toList()));
        typeList.sort(Comparator.comparing(DictTypeTree::getLevel));
        return TreeBuilder.build(typeList);
    }

    private void assemblyQueryParameter(Map<String, Object> parameters, DictTypeTree conditions) {
        String key = conditions.getParentKeys();
        if (StringUtils.isNotBlank(key)) {
            key = key.substring(0, 8);
        } else {
            key = conditions.getId();
        }
        parameters.put(FIELD_ID, key);
        parameters.put(FIELD_PARENT_KEYS, SqlParamUtils.fuzzyQuery(key));
    }

    @SuppressWarnings("unused")
    private void getEnumDict(QueryDictTree queryDictTree, String name, List<DictTypeTree> result) {}

    @Override
    public void deleteDictType(String id) {
        if (!dictProperties.isAllowedCascadeDelete()) {
            List<DictTypeTree> types = dictTypeMapper.selectList(
                    Wrappers.lambdaQuery(DictTypeTree.class).eq(DictTypeTree::getParentId, id));
            Assert.isTrue(types.isEmpty(), MSG_DICT_EXISTED_CHILD_TYPE);
        }
        Set<String> dictTypeIds = new HashSet<>(16);
        Set<String> dictTypes = new HashSet<>(16);
        List<DictTypeTree> cascadeDictTypeTree = dictTypeMapper.selectList(
                new LambdaQueryWrapper<DictTypeTree>().eq(StrUtil.isNotEmpty(id), DictTypeTree::getId, id));
        if (CollectionUtils.isNotEmpty(cascadeDictTypeTree)) {
            List<DictTypeTree> flatList = new ArrayList<>();
            // 拍平所有子节点
            flatMap(cascadeDictTypeTree, flatList, DictTypeTree::getChildren);
            flatList.forEach(dictType -> {
                dictTypeIds.add(dictType.getId());
                dictTypes.add(dictType.getDictType());
            });
        }
        if (CollectionUtils.isNotEmpty(dictTypes)) {
            dictInfoMapper.delete(Wrappers.lambdaQuery(DictInfo.class).in(DictInfo::getDictType, dictTypes));
        }
        if (CollectionUtils.isNotEmpty(dictTypeIds)) {
            dictTypeMapper.deleteByIds(dictTypeIds);
        }
    }

    /**
     * 将树形结构所有节点的数据平铺
     */
    public static <T> void flatMap(List<T> list, List<T> target, Function<T, List<T>> supplier) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        for (T t : list) {
            target.add(t);
            List<T> children = supplier.apply(t);
            if (CollectionUtils.isNotEmpty(children)) {
                flatMap(children, target, supplier);
            }
        }
    }

    @Override
    public List<DictTypeTree> getDictTypeList(String type) {
        return dictTypeMapper.getDictTypeList(StringUtils.isNotBlank(type) ? SqlParamUtils.fuzzyQuery(type) : type);
    }

    @Override
    public DictTypeTree compositeDict(String dictTypeId) {
        final DictTypeTree dictTypeTree = getById(dictTypeId);
        try {
            for (DictSourceResolver dynamicDictResolve : dynamicDictResolves) {
                if (dynamicDictResolve.isSupport(dictTypeTree.getDataType())) {
                    final List<DynamicDictSource> list = dynamicDictResolve.doResolve(dictTypeTree);
                    List<DictInfo> dictionaryEntries = new ArrayList<>(list.size());
                    AtomicBoolean hasRank = new AtomicBoolean(false);
                    if (CollectionUtils.isNotEmpty(list)) {
                        list.forEach(v -> {
                            final DictInfo dictInfo = new DictInfo();
                            dictInfo.setId(
                                    StringUtils.isNotBlank(v.getId())
                                            ? v.getId()
                                            : v.getVal().toString());
                            dictInfo.setDictType(dictTypeTree.getDictType());
                            dictInfo.setDictName(dictTypeTree.getDictName());
                            dictInfo.setFieldType(v.getVal().toString());
                            dictInfo.setFieldName(v.getKey());
                            dictInfo.setParentId(v.getPid());
                            dictInfo.setSelectable(v.getSelectable());
                            if (v.getLevel() != null) {
                                hasRank.set(true);
                                dictInfo.setLevel(v.getLevel());
                            }
                            dictionaryEntries.add(dictInfo);
                        });
                    }
                    List<DictInfo> dictionaryEntryList;
                    if (hasRank.get()) {
                        // 有级别
                        dictionaryEntryList = TreeBuilder.build3(
                                dictionaryEntries,
                                DictInfo::getId,
                                DictInfo::getParentId,
                                DictInfo::level,
                                DictInfo::setChildren);
                    } else {
                        // 无级别
                        dictionaryEntryList = TreeBuilder.build2(
                                dictionaryEntries, DictInfo::getId, DictInfo::getParentId, DictInfo::setChildren);
                    }
                    dictTypeTree.setData(dictionaryEntryList);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("解析动态字典异常 ", e);
        }
        return dictTypeTree;
    }
}
