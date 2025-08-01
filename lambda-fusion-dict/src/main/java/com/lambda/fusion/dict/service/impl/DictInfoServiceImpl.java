package com.lambda.fusion.dict.service.impl;

import static com.lambda.fusion.core.Constants.JOINER;

import cn.hutool.core.bean.BeanUtil;
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
import com.lambda.fusion.core.base.service.BaseServiceImpl;
import com.lambda.fusion.core.base.user.LoginUserDetails;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.utils.ParameterUtils;
import com.lambda.fusion.dict.dao.entity.DictInfo;
import com.lambda.fusion.dict.dao.entity.DictInfoGroup;
import com.lambda.fusion.dict.dao.entity.DictType;
import com.lambda.fusion.dict.dao.mapper.DictInfoMapper;
import com.lambda.fusion.dict.dao.mapper.DictTypeMapper;
import com.lambda.fusion.dict.service.DictInfoService;
import com.lambda.fusion.dict.common.enums.DictContextHolders;
import com.lambda.fusion.dict.vo.DictInfoVO;
import com.lambda.fusion.dict.vo.DictTypeVo;
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
public class DictInfoServiceImpl extends BaseServiceImpl<DictInfo, DictInfoVO, DictInfoMapper>
        implements DictInfoService, IService<DictInfo> {

    private final Gson gson;

    private final DictInfoMapper dictInfoMapper;

    private final DictTypeMapper dictTypeMapper;

    @Override
    public Page<DictInfo> page(Page<DictInfo> pageable, Map<String, Object> parameters) {
        pageable = dictInfoMapper.page(pageable, parameters);
        pageable.getRecords().forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return pageable;
    }

    @Override
    public List<DictInfo> selectDictInfo(Map<String, Object> parameters) {
        List<DictInfo> outcomes = dictInfoMapper.selectDictInfo(parameters);
        if (CollectionUtils.isEmpty(outcomes)) {
            return Collections.emptyList();
        }
        outcomes.forEach(info -> {
            info.setParameters(convertMap(info.getExtra()));
            info.setExtra(StringUtils.EMPTY);
        });
        return TreeFactory.build(outcomes);
    }

    @Override
    public DictInfo saveDictInfo(LoginUser operator, DictInfo source) {
        String dictType = source.getDictType();
        String fieldType = source.getFieldType();
        String fieldName = source.getFieldName();
        Assert.notNull(source, "");
        Assert.hasText(dictType, "fx.dictionary.dict.type.notempty");
        Assert.hasText(fieldType, "fx.dictionary.dict.fieldtype.notempty");
        Assert.hasText(fieldName, "fx.dictionary.dict.field.name.notempty");
        Assert.notNull(source.getSort(), "fx.dictionary.dict.sort.number.notempty");
        Assert.notNull(source.getEnableState(), "fx.dictionary.dict.enabled.notempty");
        source.setExtra(
                CollectionUtils.isNotEmpty(source.getParameters()) ? convertJson(source.getParameters()) : null);
        if (StringUtils.isNotBlank(source.getParentId())) {
            DictInfo parent = dictInfoMapper.selectById(source.getParentId());
            if (null != parent) {
                source.setParentkeys(parent.buildParentKeys());
                source.setLevel(parent.getLevel() + 1);
            }
        } else {
            source.setLevel(1);
        }
        dictInfoMapper.insert(source);
        DictInfo dictInfo = dictInfoMapper.selectById(source.getId());
        dictInfo.setParameters(convertMap(dictInfo.getExtra()));
        return dictInfo;
    }

    public boolean dictInfoExists(DictInfo dictInfo) {
        LambdaQueryWrapper<DictInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictInfo::getDictType, dictInfo.getDictType());
        wrapper.eq(DictInfo::getFieldType, dictInfo.getFieldType());
        // 租户
        if (StringUtils.isNotBlank(dictInfo.getTenantId())) {
            wrapper.eq(DictInfo::getTenantId, dictInfo.getTenantId());
        }
        DictInfo target = dictInfoMapper.selectOne(wrapper);
        return target != null && !target.getId().equals(dictInfo.getId());
    }

    @Override
    public DictInfo updateDictInfo(DictInfo dictInfo) {
        String id = dictInfo.getId();
        Assert.notNull(id, "fx.dictionary.dict.id.notempty");
        Assert.notNull(dictInfoMapper.selectById(id), "fx.dictionary.dict.update.data.not.existed");
        Assert.hasText(dictInfo.getFieldName(), "fx.dictionary.dict.field.name.notempty");
        Assert.notNull(dictInfo.getSort(), "fx.dictionary.dict.sort.number.notempty");
        Assert.notNull(dictInfo.getEnableState(), "fx.dictionary.dict.enabled.notempty");
        dictInfo.setExtra(
                CollectionUtils.isNotEmpty(dictInfo.getParameters()) ? convertJson(dictInfo.getParameters()) : null);
        if (dictInfoExists(dictInfo)) {
            dictInfo.setFieldType(null);
        }
        dictInfoMapper.updateById(dictInfo);
        return dictInfoMapper.selectById(id);
    }

    @Override
    public void changeState(int state, String id) {
        DictInfo parameter = new DictInfo();
        parameter.setId(id);
        parameter.setEnableState(state);
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public void changeSelectable(int state, String id) {
        DictInfo parameter = new DictInfo();
        parameter.setId(id);
        parameter.setSelectable(state);
        dictInfoMapper.updateById(parameter);
    }

    @Override
    public Map<String, Object> getStaticDictInfoGroup(String type) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(type) ? ParameterUtils.fuzzyQuery(type) : type, tenantId);
        Map<String, Object> map = Maps.newHashMapWithExpectedSize(lists.size());
        for (DictInfoGroup info : lists) {
            map.put(info.getDictType(), info.getDictList());
        }
        return map;
    }

    @Override
    public Map<String, DictTypeVo> getDynamicDictInfoGroup(String type) {
        final LambdaQueryWrapper<DictType> query = Wrappers.lambdaQuery(DictType.class);
        if (StringUtils.isNotEmpty(type)) {
            query.like(DictType::getDictType, type);
        }
        final List<DictType> dictTypes = dictTypeMapper.selectList(query);
        Map<String, DictTypeVo> result = Maps.newHashMapWithExpectedSize(dictTypes.size());

        // Enum List
        final List<DictType> enumList = DictContextHolders.getDictTypeList();
        if (CollectionUtils.isNotEmpty(enumList)) {
            dictTypes.addAll(enumList);
        }

        for (DictType dictType : dictTypes) {
            final DictTypeVo vo = BeanUtil.copyProperties(dictType, DictTypeVo.class);
            result.put(vo.getDictType(), vo);
        }
        String tenantId = OperatorUtils.getOperator().getTenantId();
        List<DictInfoGroup> lists = dictInfoMapper.getAllDictInfoGroup(
                StringUtils.isNotBlank(type) ? ParameterUtils.fuzzyQuery(type) : type, tenantId);
        for (DictInfoGroup info : lists) {
            if (result.containsKey(info.getDictType())) {
                final DictTypeVo vo = result.get(info.getDictType());
                vo.setData(info.getDictList());
            }
        }
        return result;
    }

    @Override
    public List<DictInfo> treeData(String type) {
        if (StringUtils.isBlank(type)) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getDictType, type);
        DictType dictType = dictTypeMapper.selectOne(wrapper);
        if (dictType == null) {
            return Collections.emptyList();
        }
        String currentKey = dictType.getParentKeys();
        if (StringUtils.isNotBlank(currentKey)) {
            if (currentKey.contains(JOINER)) {
                ids = Arrays.asList(currentKey.split(JOINER));
            } else {
                ids.add(currentKey);
            }
        } else {
            ids.add(dictType.getId());
        }
        LoginUserDetails operator = ((LoginUserDetails) OperatorUtils.getOperator());
        List<DictInfo> outcomes = dictInfoMapper.treeList(ids, operator.getTenantId());
        return TreeFactory.build(outcomes);
    }

    @Override
    public List<DictInfo> subTreeData(String type) {
        List<DictInfo> outcomes = new ArrayList<>();
        if (StringUtils.isNotBlank(type)) {
            LoginUserDetails operator = ((LoginUserDetails) OperatorUtils.getOperator());
            LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DictType::getDictType, type);
            DictType dictType = dictTypeMapper.selectOne(wrapper);
            String currentKey = dictType.getId();
            LambdaQueryWrapper<DictType> conditions = new LambdaQueryWrapper<>();
            conditions.likeRight(DictType::getParentKeys, currentKey).or().eq(DictType::getId, currentKey);
            List<DictType> dictTypes = dictTypeMapper.selectList(conditions);
            if (CollectionUtils.isNotEmpty(dictTypes)) {
                List<String> ids = dictTypes.stream().map(DictType::id).collect(Collectors.toList());
                List<DictInfo> list = dictInfoMapper.treeList(ids, operator.getTenantId());
                list.forEach(info -> info.setParameters(
                        StringUtils.isNotBlank(info.getExtra()) ? convertMap(info.getExtra()) : null));
                outcomes = TreeFactory.build(list);
            }
        }
        outcomes.sort(Comparator.comparing(DictInfo::getSort));
        return outcomes;
    }

    @Override
    public List<DictInfo> queryDictInfoByParentId(String parentId) {
        DictInfo dictInfo = dictInfoMapper.selectById(parentId);
        if (Objects.isNull(dictInfo)) {
            return Collections.emptyList();
        }
        return TreeFactory.build(queryParentDictInfo(dictInfo));
    }

    @Override
    public void deleteDictInfoById(String id) {
        dictInfoMapper.deleteById(id);
    }

    private List<DictInfo> queryParentDictInfo(DictInfo dictInfo) {
        DictInfo wrapper = new DictInfo();
        String keys = dictInfo.getParentkeys();
        if (StringUtils.isNotBlank(keys)) {
            wrapper.setParentId(ParameterUtils.fuzzyQuery(keys.substring(keys.length() - 8)));
        } else {
            wrapper.setId(dictInfo.getId());
        }
        wrapper.setLevel(dictInfo.getLevel());
        wrapper.setDictType(dictInfo.getDictType());
        LoginUserDetails operator = ((LoginUserDetails) OperatorUtils.getOperator());
        wrapper.setTenantId(operator.getTenantId());

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

    @Override
    protected DictInfoVO entityVO(DictInfo entity) {
        return null;
    }

    private static class MapTypeToken extends TypeToken<Map<String, Object>> {}
}
