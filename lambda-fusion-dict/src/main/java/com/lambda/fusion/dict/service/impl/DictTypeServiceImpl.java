package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.dict.support.constants.DictConstants.*;

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
import com.lambda.fusion.dict.DictionaryProperties;
import com.lambda.fusion.dict.mapper.DictInfoMapper;
import com.lambda.fusion.dict.mapper.DictTypeMapper;
import com.lambda.fusion.dict.model.DictInfo;
import com.lambda.fusion.dict.model.DictTypeTree;
import com.lambda.fusion.dict.model.QueryDictTree;
import com.lambda.fusion.dict.service.DictTypeService;
import com.lambda.fusion.dict.support.enums.DictionaryRegistry;
import com.lambda.fusion.dict.support.model.DynamicDictionarySource;
import com.lambda.fusion.dict.support.resolve.DictionarySourceResolver;
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

    private final List<DictionarySourceResolver> dynamicDictResolves;

    private final DictionaryProperties dictionaryProperties;

    @Override
    public DictTypeTree saveDictType(DictTypeTree source) {
        source.setId(IdUtil.getSnowflakeNextIdStr());
        source.setDictType(Optional.ofNullable(source.getDictType()).orElse(source.getId()));
        Assert.notNull(source, "字典类型不能为空");
        Assert.hasText(source.getDictName(), "字典名称不存在");
        Assert.isFalse(dictTypeExists(source), MSG_DICT_TYPE_NOT_EXISTED);
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
        source.setCreateUser(OperatorUtils.getOperator().getName());
        source.setCreateTime(LocalDateTime.now());
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

    public List<DictTypeTree> staticTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        if (StringUtils.isNotBlank(type)) {
            DictTypeTree conditions = dictTypeMapper.selectOne(new QueryWrapper<DictTypeTree>().eq("dict_type", type));
            Assert.notNull(conditions, MSG_DICT_TYPE_NOT_EXISTED);
            String key = conditions.getParentKeys();
            if (StringUtils.isNotBlank(key)) {
                key = key.substring(0, 8);
            } else {
                key = conditions.getId();
            }
            parameters.put("id", key);
            parameters.put("parentKeys", SqlParamUtils.fuzzyQuery(key));
            parameters.put("level", conditions.getLevel());
        }
        return TreeBuilder.build(dictTypeMapper.treeList(parameters));
    }

    @Override
    public List<DictTypeTree> dynamicTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        String name = queryDictTree.getName();
        Integer dataType = queryDictTree.getDataType();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        if (StringUtils.isNotBlank(name)) {
            parameters.put("name", SqlParamUtils.fuzzyQuery(name));
        }

        List<DictTypeTree> result = new ArrayList<>();
        if (dataType == null || dataType.equals(DATA_TYPE_ENUM)) {
            // 枚举字典
            if (StringUtils.isNotBlank(type)) {
                DictTypeTree conditions = dictTypeMapper.selectOne(
                        new LambdaQueryWrapper<DictTypeTree>().eq(DictTypeTree::getDictType, type));
                if (Objects.nonNull(conditions)) {
                    String key = conditions.getParentKeys();
                    if (StringUtils.isNotBlank(key)) {
                        key = key.substring(0, 8);
                    } else {
                        key = conditions.getId();
                    }
                    parameters.put("id", key);
                    parameters.put("parentKeys", SqlParamUtils.fuzzyQuery(key));
                }
                getEnumDict(queryDictTree, name, result);
            } else {
                final List<DictTypeTree> enumList = DictionaryRegistry.getDictTypeList();
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
        if (dataType == null || !dataType.equals(DATA_TYPE_ENUM)) {
            // 非枚举字典
            parameters.put("dataType", dataType);
            parameters.put("userOnly", queryDictTree.isUserOnly());
            result.addAll(dictTypeMapper.treeList(parameters));
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

    private void getEnumDict(QueryDictTree queryDictTree, String name, List<DictTypeTree> result) {
        final DictTypeTree enumDictTypeTree = DictionaryRegistry.getDictType(queryDictTree.getType());
        if (Objects.nonNull(enumDictTypeTree)) {
            if (StringUtils.isNotBlank(name)) {
                if (StringUtils.contains(enumDictTypeTree.getDictName(), name)) {
                    result.add(enumDictTypeTree);
                }
            } else {
                result.add(enumDictTypeTree);
            }
        }
    }

    @Override
    public void deleteDictType(String id) {
        if (!dictionaryProperties.isAllowedCascadeDelete()) {
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
     *
     * @param list
     * @param target
     * @param supplier
     * @param <T>
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
    public DictTypeTree dynamicDict(String dictTypeId) {
        final DictTypeTree dictTypeTree = getById(dictTypeId);
        try {
            for (DictionarySourceResolver dynamicDictResolve : dynamicDictResolves) {
                if (dynamicDictResolve.isSupport(dictTypeTree.getDataType())) {
                    final List<DynamicDictionarySource> list = dynamicDictResolve.doResolve(dictTypeTree);
                    List<DictInfo> dictionaryEntries = new ArrayList<>(list.size());
                    AtomicBoolean hasRank = new AtomicBoolean(false);
                    if (CollectionUtils.isNotEmpty(list)) {
                        list.forEach(v -> {
                            final DictInfo dictionaryEntry = new DictInfo();
                            dictionaryEntry.setId(
                                    StringUtils.isNotBlank(v.getId())
                                            ? v.getId()
                                            : v.getVal().toString());
                            dictionaryEntry.setDictType(dictTypeTree.getDictType());
                            dictionaryEntry.setDictName(dictTypeTree.getDictName());
                            dictionaryEntry.setFieldType(v.getVal().toString());
                            dictionaryEntry.setFieldName(v.getKey());
                            dictionaryEntry.setParentId(v.getPid());
                            dictionaryEntry.setSelectable(v.getSelectable());
                            if (v.getLevel() != null) {
                                hasRank.set(true);
                                dictionaryEntry.setLevel(v.getLevel());
                            }
                            dictionaryEntries.add(dictionaryEntry);
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
