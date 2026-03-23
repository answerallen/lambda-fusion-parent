package com.lambda.fusion.datascope.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.datascope.mapper.DataScopeMapper;
import com.lambda.fusion.datascope.model.DataScopeEntity;
import com.lambda.fusion.datascope.model.GrantDataScope;
import com.lambda.fusion.datascope.model.PurviewNode;
import com.lambda.fusion.datascope.commons.provider.DataViewProvider;
import com.lambda.fusion.datascope.service.DataScopeGrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeGrantServiceImpl implements DataScopeGrantService {

    private final DataScopeMapper dataScopeMapper;
    private final List<DataViewProvider> dataViewProviders;

    @Override
    public List<PurviewNode> getDataScopeTree(int type, String targetId, String targetType) {
        UserDetails userDetails = AuthUtils.getUser();
        String tenantId = userDetails != null ? userDetails.getTenantId() : null;

        // 1. 找到对应的策略提供者
        DataViewProvider provider = dataViewProviders.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported data scope type: " + type));

        // 2. 加载完整的业务视图节点
        List<PurviewNode> allNodes = provider.loadDataView(tenantId);
        if (CollectionUtils.isEmpty(allNodes)) {
            return List.of();
        }

        // 3. 查询当前 target 已经授权的节点
        LambdaQueryWrapper<DataScopeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataScopeEntity::getTid, targetId)
                .eq(DataScopeEntity::getTargetType, targetType)
                .eq(DataScopeEntity::getDomainType, type);
        List<DataScopeEntity> grantedList = dataScopeMapper.selectList(wrapper);
        Map<String, Integer> grantedMap = grantedList.stream()
                .collect(Collectors.toMap(DataScopeEntity::getId, DataScopeEntity::getChecked));

        // 4. 回填选中状态
        for (PurviewNode node : allNodes) {
            Integer checked = grantedMap.getOrDefault(node.getId(), 0);
            node.setChecked(checked);
        }

        // 5. 构建树形结构
        return TreeBuilder.build(allNodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantDataScope(GrantDataScope req) {
        UserDetails userDetails = AuthUtils.getUser();
        String tenantId = userDetails != null ? userDetails.getTenantId() : null;

        // 1. 删除旧的授权数据
        dataScopeMapper.deleteByTarget(req.getTargetId(), req.getTargetType(), req.getType());

        // 2. 插入新的授权数据
        if (CollectionUtils.isNotEmpty(req.getNodes())) {
            List<DataScopeEntity> entities = req.getNodes().stream().map(node -> {
                DataScopeEntity entity = new DataScopeEntity();
                entity.setId(node.getId());
                entity.setTid(req.getTargetId());
                entity.setTargetType(req.getTargetType());
                entity.setDomainType(req.getType());
                entity.setRankLevel(node.getLevel());
                entity.setChecked(node.getChecked());
                entity.setTenantId(tenantId);
                return entity;
            }).collect(Collectors.toList());

            dataScopeMapper.batchInsert(entities);
        }
    }
}
