package com.lambda.fusion.authority.service.impl;

import com.lambda.fusion.authority.domain.area.*;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.AreaMapper;
import com.lambda.fusion.authority.service.AreaService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 行政区划服务实现
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
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
        if (areaMapper.hasExists(createArea.getAreaCode())) {
            throw AuthorityBusinessException.operationNotSupported("区域编码已存在");
        }
        areaMapper.insert(createArea.toEntity());
        return areaMapper.selectByAreaCode(createArea.getAreaCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Area updateArea(UpdateArea updateArea) {
        // 检查区域是否存在
        Area existing = areaMapper.selectByAreaCode(updateArea.getAreaCode());
        if (existing == null) {
            throw AuthorityBusinessException.areaNotFound(updateArea.getAreaCode());
        }
        areaMapper.updateById(updateArea.toEntity());
        return areaMapper.selectByAreaCode(updateArea.getAreaCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArea(String areaCode) {
        // 检查区域是否存在
        if (!areaMapper.hasExists(areaCode)) {
            throw AuthorityBusinessException.areaNotFound(areaCode);
        }
        // 检查是否存在子区域
        if (areaMapper.hasChildren(areaCode)) {
            throw AuthorityBusinessException.operationNotSupported("该区域下存在子区域，无法删除");
        }

        areaMapper.deleteByAreaCode(areaCode);
    }

    @Override
    public boolean checkAreaCode(String areaCode) {
        return areaMapper.hasExists(areaCode);
    }
}
