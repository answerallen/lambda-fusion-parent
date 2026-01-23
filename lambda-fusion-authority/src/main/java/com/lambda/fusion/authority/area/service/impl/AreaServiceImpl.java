package com.lambda.fusion.authority.area.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.area.mapper.AreaMapper;
import com.lambda.fusion.authority.area.model.*;
import com.lambda.fusion.authority.area.service.AreaService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 行政区划服务实现
 */
@Service
@RequiredArgsConstructor
public class AreaServiceImpl implements AreaService {

    private final AreaMapper areaMapper;

    @Override
    public List<Area> getAreas(AreaQuery query) {
        return areaMapper.selectAreas(query);
    }

    @Override
    public List<AreaTree> getAreaTree(String parentCode) {
        List<AreaTree> areas = areaMapper.selectAreaTree(parentCode);
        return TreeBuilder.build(areas);
    }

    @Override
    public Area getByAreaCode(String areaCode) {
        return areaMapper.selectByAreaCode(areaCode);
    }

    @Override
    public List<Area> getChildren(String parentCode) {
        return areaMapper.selectChildren(parentCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Area addArea(CreateArea createArea) {
        // 检查区域编码是否已存在
        Assert.isTrue(!areaMapper.hasExists(createArea.getAreaCode()), "区域编码已存在");
        areaMapper.insert(createArea.toEntity());
        return areaMapper.selectByAreaCode(createArea.getAreaCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Area updateArea(UpdateArea updateArea) {
        // 检查区域是否存在
        Area existing = areaMapper.selectByAreaCode(updateArea.getAreaCode());
        Assert.notNull(existing, "区域不存在");
        areaMapper.updateById(updateArea.toEntity());
        return areaMapper.selectByAreaCode(updateArea.getAreaCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArea(String areaCode) {
        // 检查区域是否存在
        Assert.isTrue(areaMapper.hasExists(areaCode), "区域不存在");
        // 检查是否存在子区域
        Assert.isTrue(!areaMapper.hasChildren(areaCode), "该区域下存在子区域，无法删除");

        areaMapper.deleteByAreaCode(areaCode);
    }

    @Override
    public boolean checkAreaCode(String areaCode) {
        return areaMapper.hasExists(areaCode);
    }
}
