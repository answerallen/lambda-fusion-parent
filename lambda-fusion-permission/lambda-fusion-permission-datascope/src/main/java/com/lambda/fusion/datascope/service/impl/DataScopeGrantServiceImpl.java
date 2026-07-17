package com.lambda.fusion.datascope.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.cloud.core.exception.IllegalArgumentException;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.datascope.mapper.DataScopeMapper;
import com.lambda.fusion.datascope.model.DataScopeEntity;
import com.lambda.fusion.datascope.model.DataScopeNode;
import com.lambda.fusion.datascope.model.GrantDataScope;
import com.lambda.fusion.datascope.provider.DataViewProvider;
import com.lambda.fusion.datascope.service.DataScopeGrantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@SuppressFBWarnings("EI_EXPOSE_REP")
@RequiredArgsConstructor
public class DataScopeGrantServiceImpl implements DataScopeGrantService {

    private final DataScopeMapper dataScopeMapper;
    private final List<DataViewProvider> dataViewProviders;

    @Override
    public List<DataScopeNode> getDataScopeTree(int type, String targetId, String targetType) {
        String tenantId = AuthUtils.getTenantId();

        // 1. 找到对应的策略提供者
        DataViewProvider provider = dataViewProviders.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported data scope type: " + type));

        // 2. 加载完整的业务视图节点
        List<DataScopeNode> allNodes = provider.loadDataView(tenantId);
        if (CollectionUtils.isEmpty(allNodes)) {
            return List.of();
        }

        // 3. 查询当前 target 已经授权的节点
        LambdaQueryWrapper<DataScopeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataScopeEntity::getTid, targetId)
                .eq(DataScopeEntity::getTargetType, targetType)
                .eq(DataScopeEntity::getDomainType, type)
                .eq(StrUtil.isNotEmpty(tenantId), DataScopeEntity::getTenantId, tenantId);
        List<DataScopeEntity> grantedList = dataScopeMapper.selectList(wrapper);
        Map<String, Integer> grantedMap = grantedList.stream()
                .collect(Collectors.toMap(DataScopeEntity::getId, DataScopeEntity::getChecked, (left, right) -> left));

        // 4. 回填选中状态
        for (DataScopeNode node : allNodes) {
            Integer checked = grantedMap.getOrDefault(node.getId(), 0);
            node.setChecked(checked);
        }

        // 5. 构建树形结构
        return TreeBuilder.build(allNodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantDataScope(GrantDataScope grantDataScope) {
        String tenantId = AuthUtils.getTenantId();

        if ("ROLE".equals(grantDataScope.getTargetType())
                && FusionConstants.ROLE_ADMIN.equals(grantDataScope.getTargetId())) {
            log.warn("Cannot grant data scope to super admin role directly");
            return;
        }

        // 1. 删除旧的授权数据
        dataScopeMapper.deleteByTarget(
                grantDataScope.getTargetId(), grantDataScope.getTargetType(), grantDataScope.getType());

        // 2. 插入新的授权数据
        if (CollectionUtils.isNotEmpty(grantDataScope.getNodes())) {
            List<DataScopeEntity> entities = grantDataScope.getNodes().stream()
                    .filter(node -> node != null && StringUtils.isNotBlank(node.getId()))
                    .map(node -> {
                        DataScopeEntity entity = new DataScopeEntity();
                        entity.setId(node.getId());
                        entity.setTid(grantDataScope.getTargetId());
                        entity.setTargetType(grantDataScope.getTargetType());
                        entity.setDomainType(grantDataScope.getType());
                        entity.setRankLevel(node.getLevel());
                        entity.setChecked(node.getChecked());
                        entity.setTenantId(tenantId);
                        return entity;
                    })
                    .collect(Collectors.toList());

            dataScopeMapper.batchInsert(entities);
        }
    }
}
