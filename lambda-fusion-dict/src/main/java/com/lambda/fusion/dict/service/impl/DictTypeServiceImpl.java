package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.autoconfig.DictionaryProperties;
import com.lambda.fusion.core.tree.ITreeDataFilter;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.utils.ParameterUtils;
import com.lambda.fusion.dict.common.enums.DictContextHolders;
import com.lambda.fusion.dict.common.model.DynamicDict;
import com.lambda.fusion.dict.common.resolve.IDynamicDictResolve;
import com.lambda.fusion.dict.dao.entity.DictInfo;
import com.lambda.fusion.dict.dao.entity.DictType;
import com.lambda.fusion.dict.dao.mapper.DictInfoMapper;
import com.lambda.fusion.dict.dao.mapper.DictTypeMapper;
import com.lambda.fusion.dict.dto.QueryDictTree;
import com.lambda.fusion.dict.service.DictTypeService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
 *
 * @author JIN
 */
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DictTypeServiceImpl extends ServiceImpl<DictTypeMapper, DictType> implements DictTypeService {

    private final DictTypeMapper dictTypeMapper;

    private final DictInfoMapper dictInfoMapper;

    private final ITreeDataFilter treeDataFilter;

    private final List<IDynamicDictResolve> dynamicDictResolves;

    private final DictionaryProperties dictionaryProperties;

    @Override
    public DictType saveDictType(DictType source) {
        source.setId(IdUtil.getSnowflakeNextIdStr());
        source.setDictType(Optional.ofNullable(source.getDictType()).orElse(source.getId()));
        Assert.notNull(source, "字典类型不能为空");
        Assert.hasText(source.getDictName(), "字典名称不存在");
        Assert.isFalse(dictTypeExists(source), MSG_DICT_TYPE_NOT_EXISTED);
        if (StringUtils.isNotBlank(source.getParentId())) {
            DictType parent = dictTypeMapper.selectById(source.getParentId());
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
        dictTypeMapper.insert(source);
        return dictTypeMapper.selectById(source.getId());
    }

    @Override
    public void updateDictType(DictType source) {
        Assert.notNull(source.getId(), MSG_DICT_ID_NOT_EMPTY);
        Assert.hasText(source.getDictName(), MSG_DICT_NAME_NOT_EMPTY);
        Assert.isFalse(dictTypeExists(source), MSG_DICT_TYPE_EXISTED);
        dictTypeMapper.updateById(source);
    }

    public boolean dictTypeExists(DictType dictType) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getDictType, dictType.getDictType())
                .or()
                .eq(DictType::getDictName, dictType.getDictName());
        DictType target = dictTypeMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictType.getId());
    }

    public List<DictType> staticTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        if (StringUtils.isNotBlank(type)) {
            DictType conditions = dictTypeMapper.selectOne(new QueryWrapper<DictType>().eq("dict_type", type));
            Assert.notNull(conditions, MSG_DICT_TYPE_NOT_EXISTED);
            String key = conditions.getParentKeys();
            if (StringUtils.isNotBlank(key)) {
                key = key.substring(0, 8);
            } else {
                key = conditions.getId();
            }
            parameters.put("id", key);
            parameters.put("parentkeys", ParameterUtils.fuzzyQuery(key));
            parameters.put("level", conditions.getLevel());
        }
        return TreeFactory.build(dictTypeMapper.treeList(parameters));
    }

    @Override
    public List<DictType> dynamicTreeList(QueryDictTree queryDictTree) {
        String type = queryDictTree.getType();
        String name = queryDictTree.getName();
        Integer dataType = queryDictTree.getDataType();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        if (StringUtils.isNotBlank(name)) {
            parameters.put("name", ParameterUtils.fuzzyQuery(name));
        }

        List<DictType> result = new ArrayList<>();
        if (dataType == null || dataType == DATA_TYPE_ENUM) {
            // 枚举字典
            if (StringUtils.isNotBlank(type)) {
                DictType conditions =
                        dictTypeMapper.selectOne(new LambdaQueryWrapper<DictType>().eq(DictType::getDictType, type));
                if (Objects.nonNull(conditions)) {
                    String key = conditions.getParentKeys();
                    if (StringUtils.isNotBlank(key)) {
                        key = key.substring(0, 8);
                    } else {
                        key = conditions.getId();
                    }
                    parameters.put("id", key);
                    parameters.put("parentkeys", ParameterUtils.fuzzyQuery(key));
                }
                getEnumDict(queryDictTree, name, result);
            } else {
                final List<DictType> enumList = DictContextHolders.getDictTypeList();
                if (CollectionUtils.isNotEmpty(enumList)) {
                    if (StringUtils.isNotBlank(name)) {
                        List<DictType> list = enumList.stream()
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
        final List<DictType> typeList = treeDataFilter.filter(
                result,
                queryDictTree.getType(),
                DictType::getDictType,
                DictType::getId,
                DictType::getParentKeys,
                target -> target.stream()
                        .sorted(Comparator.comparing(DictType::getDictName))
                        .collect(Collectors.toList()));
        typeList.sort(Comparator.comparing(DictType::getLevel));
        return TreeFactory.build(typeList);
    }

    private void getEnumDict(QueryDictTree queryDictTree, String name, List<DictType> result) {
        final DictType enumDictType = DictContextHolders.getDictType(queryDictTree.getType());
        if (Objects.nonNull(enumDictType)) {
            if (StringUtils.isNotBlank(name)) {
                if (StringUtils.contains(enumDictType.getDictName(), name)) {
                    result.add(enumDictType);
                }
            } else {
                result.add(enumDictType);
            }
        }
    }

    @Override
    public void deleteDictType(String id) {
        if (!dictionaryProperties.isAllowedCascadeDelete()) {
            List<DictType> types = dictTypeMapper.selectList(
                    Wrappers.lambdaQuery(DictType.class).eq(DictType::getParentId, id));
            Assert.isTrue(types.isEmpty(), MSG_DICT_EXISTED_CHILD_TYPE);
        }
        Set<String> dictTypeIds = new HashSet<>(16);
        Set<String> dictTypes = new HashSet<>(16);
        List<DictType> cascadeDictType = dictTypeMapper.selectList(
                new LambdaQueryWrapper<DictType>().eq(StrUtil.isNotEmpty(id), DictType::getId, id));
        if (CollectionUtils.isNotEmpty(cascadeDictType)) {
            List<DictType> flatList = new ArrayList<>();
            // 拍平所有子节点
            flatMap(cascadeDictType, flatList, DictType::getChildren);
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
    public List<DictType> getDictTypeList(String type) {
        return dictTypeMapper.getDictTypeList(StringUtils.isNotBlank(type) ? ParameterUtils.fuzzyQuery(type) : type);
    }

    @Override
    public Page<DictType> queryDictTypePage(Page<DictType> pagination, Map<String, String> parameters) {
        return dictTypeMapper.pageDictType(pagination, parameters);
    }

    @Override
    public DictType dynamicDict(String dictTypeId) {
        final DictType dictType = getById(dictTypeId);
        try {
            for (IDynamicDictResolve dynamicDictResolve : dynamicDictResolves) {
                if (dynamicDictResolve.isSupport(dictType.getDataType())) {
                    final List<DynamicDict> list = dynamicDictResolve.doResolve(dictType);
                    List<DictInfo> dictInfos = new ArrayList<>(list.size());
                    AtomicBoolean hasRank = new AtomicBoolean(false);
                    if (CollectionUtils.isNotEmpty(list)) {
                        list.forEach(v -> {
                            final DictInfo dictInfo = new DictInfo();
                            dictInfo.setId(
                                    StringUtils.isNotBlank(v.getId())
                                            ? v.getId()
                                            : v.getVal().toString());
                            dictInfo.setDictType(dictType.getDictType());
                            dictInfo.setDictName(dictType.getDictName());
                            dictInfo.setFieldType(v.getVal().toString());
                            dictInfo.setFieldName(v.getKey());
                            dictInfo.setParentId(v.getPid());
                            dictInfo.setSelectable(v.getSelectable());
                            if (v.getLevel() != null) {
                                hasRank.set(true);
                                dictInfo.setLevel(v.getLevel());
                            }
                            dictInfos.add(dictInfo);
                        });
                    }
                    List<DictInfo> dictInfoList;
                    if (hasRank.get()) {
                        // 有级别
                        dictInfoList = TreeFactory.build3(
                                dictInfos,
                                DictInfo::getId,
                                DictInfo::getParentId,
                                DictInfo::level,
                                DictInfo::setChildren);
                    } else {
                        // 无级别
                        dictInfoList = TreeFactory.build2(
                                dictInfos, DictInfo::getId, DictInfo::getParentId, DictInfo::setChildren);
                    }
                    dictType.setData(dictInfoList);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("解析动态字典异常 ", e);
        }
        return dictType;
    }
}
