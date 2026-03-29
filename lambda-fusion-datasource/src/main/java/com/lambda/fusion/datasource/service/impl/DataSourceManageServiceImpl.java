package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.commons.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.commons.event.DataSourceEvent;
import com.lambda.fusion.datasource.commons.tenant.TenantIsolationResolver;
import com.lambda.fusion.datasource.commons.util.DataSourcePropertyUtils;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.*;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
public class DataSourceManageServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceManageService {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantDataSourceMapper tenantDataSourceMapper;
    private final TenantIsolationResolver tenantIsolationResolver;
    private final ObjectProvider<RemoteDataSourceService> remoteDataSourceServiceProvider;

    public DataSourceManageServiceImpl(
            DynamicDataSourceService dynamicDataSourceService,
            ApplicationEventPublisher eventPublisher,
            TenantDataSourceMapper tenantDataSourceMapper,
            TenantIsolationResolver tenantIsolationResolver,
            ObjectProvider<RemoteDataSourceService> remoteDataSourceServiceProvider) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.eventPublisher = eventPublisher;
        this.tenantDataSourceMapper = tenantDataSourceMapper;
        this.tenantIsolationResolver = tenantIsolationResolver;
        this.remoteDataSourceServiceProvider = remoteDataSourceServiceProvider;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<DataSourceEntity> listAll() {
        return list(Wrappers.lambdaQuery(DataSourceEntity.class).orderByAsc(DataSourceEntity::getId));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<DataSourceEntity> page(QueryDataSource queryDTO) {
        return page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UpsertDataSource upsertDataSource) {
        Assert.notNull(upsertDataSource, "input is null");
        DataSourceEntity entity = upsertDataSource.toEntity();
        if (upsertDataSource.getStatus() == null) {
            entity.setStatus(DatasourceConstants.DatasourceStatus.fromCode(1));
        }
        boolean saved = save(entity);
        Assert.isTrue(saved, "save failed");
        syncDynamicDataSource(entity);
        publishAdd(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpsertDataSource upsertDataSource) {
        Assert.hasText(id, "id is blank");
        Assert.notNull(upsertDataSource, "input is null");
        DataSourceEntity existing = getById(id);
        Assert.notNull(existing, "entity not found");
        DataSourceEntity entity = upsertDataSource.toEntity();
        entity.setId(id);
        if (!StringUtils.hasText(entity.getPassword())) {
            entity.setPassword(existing.getPassword());
        }
        if (entity.getStatus() == null) {
            entity.setStatus(existing.getStatus());
        }
        boolean updated = updateById(entity);
        Assert.isTrue(updated, "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
        long bindingCount = tenantDataSourceMapper.selectCount(
                Wrappers.lambdaQuery(TenantDataSourceEntity.class).eq(TenantDataSourceEntity::getDatasourceId, id));
        Assert.isTrue(bindingCount == 0, "数据源已被租户绑定，无法删除");
        DataSourceEntity existing = getById(id);
        if (existing != null) {
            RemoteDataSource dto = new RemoteDataSource();
            dto.setId(id);
            dto.setDatasourceName(existing.getDatasourceName());
            eventPublisher.publishEvent(DataSourceEvent.remove(this, dto));
        }
        removeById(id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public boolean test(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        DataSourceProperty dataSourceProperty = DataSourcePropertyUtils.getDataSourceProperty(entity);
        return dynamicDataSourceService.test(dataSourceProperty);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        Assert.hasText(id, "id is blank");
        boolean test = test(id);
        Assert.isTrue(test, "数据源测试失败");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        if (entity.getStatus() != null
                && DatasourceConstants.DatasourceStatus.ONLINE.equals(entity.getStatus())) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setStatus(DatasourceConstants.DatasourceStatus.fromCode(1));
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        entity.setStatus(DatasourceConstants.DatasourceStatus.OFFLINE);
        entity.setUpdatedAt(LocalDateTime.now());
        Assert.isTrue(updateById(entity), "update failed");
        // 禁用需发 REMOVE 事件，Client 端才会调用 removeDataSource() 移除连接池
        publishRemove(entity);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public TenantDataSourceEntity getTenantDataSource(String tenantId, FusionConstants.DatabaseUsageType usageType) {
        Assert.hasText(tenantId, "tenantId is blank");
        Assert.notNull(usageType, "usageType is null");
        if (FusionConstants.DatabaseUsageType.TENANT.equals(usageType)) {
            return tenantDataSourceMapper.selectOne(Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                    .eq(TenantDataSourceEntity::getTenantId, tenantId)
                    .and(wrapper -> wrapper.eq(TenantDataSourceEntity::getUsageType, usageType)
                            .or()
                            .isNull(TenantDataSourceEntity::getUsageType))
                    .last("limit 1"));
        }
        return tenantDataSourceMapper.selectOne(Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                .eq(TenantDataSourceEntity::getTenantId, tenantId)
                .eq(TenantDataSourceEntity::getUsageType, usageType)
                .last("limit 1"));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<TenantDataSourceEntity> getTenantDataSources(String tenantId) {
        Assert.hasText(tenantId, "tenantId is blank");
        return tenantDataSourceMapper.selectList(
                Wrappers.lambdaQuery(TenantDataSourceEntity.class).eq(TenantDataSourceEntity::getTenantId, tenantId));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<TenantDataSourceEntity> listTenantDataSources(List<String> tenantIds) {
        if (CollectionUtils.isEmpty(tenantIds)) {
            return Collections.emptyList();
        }
        return tenantDataSourceMapper.selectList(
                Wrappers.lambdaQuery(TenantDataSourceEntity.class).in(TenantDataSourceEntity::getTenantId, tenantIds));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<DataSourceBindingStatus> listDataSourceBindingStatuses(List<String> datasourceIds) {
        if (CollectionUtils.isEmpty(datasourceIds)) {
            return Collections.emptyList();
        }
        Map<String, DataSourceBindingStatus> statusMap = new LinkedHashMap<>();
        for (String datasourceId : datasourceIds) {
            if (!StringUtils.hasText(datasourceId)) {
                continue;
            }
            DataSourceBindingStatus status = new DataSourceBindingStatus();
            status.setDatasourceId(datasourceId);
            status.setTenantBindingTenantId(null);
            status.setTenantSchemaInitialized(Boolean.FALSE);
            status.setAiBindingTenantId(null);
            status.setTenantBindingConflict(Boolean.FALSE);
            status.setAiBindingConflict(Boolean.FALSE);
            statusMap.put(datasourceId, status);
        }
        if (statusMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<TenantDataSourceEntity> bindings =
                tenantDataSourceMapper.selectList(Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                        .in(TenantDataSourceEntity::getDatasourceId, statusMap.keySet()));
        for (TenantDataSourceEntity binding : bindings) {
            DataSourceBindingStatus status = statusMap.get(binding.getDatasourceId());
            if (status == null) {
                continue;
            }
            FusionConstants.DatabaseUsageType usageType = binding.getUsageType();
            if (FusionConstants.DatabaseUsageType.AI.equals(usageType)) {
                if (!StringUtils.hasText(status.getAiBindingTenantId())) {
                    status.setAiBindingTenantId(binding.getTenantId());
                } else if (!status.getAiBindingTenantId().equals(binding.getTenantId())) {
                    status.setAiBindingConflict(Boolean.TRUE);
                }
                continue;
            }
            if (!StringUtils.hasText(status.getTenantBindingTenantId())) {
                status.setTenantBindingTenantId(binding.getTenantId());
                status.setTenantSchemaInitialized(binding.isSchemaInitialized());
            } else if (!status.getTenantBindingTenantId().equals(binding.getTenantId())) {
                status.setTenantBindingConflict(Boolean.TRUE);
            }
        }
        return List.copyOf(statusMap.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTenantDataSource(
            String tenantId, String datasourceId, FusionConstants.DatabaseUsageType usageType) {
        Assert.hasText(tenantId, "tenantId is blank");
        Assert.hasText(datasourceId, "datasourceId is blank");
        Assert.notNull(usageType, "usageType is null");
        Assert.isTrue(tenantIsolationResolver.isDedicated(tenantId), "仅独立库租户允许配置数据源");
        DataSourceEntity dataSourceEntity = getById(datasourceId);
        Assert.notNull(dataSourceEntity, "datasource not found");
        Assert.notNull(dataSourceEntity.getUsageType(), "datasource usageType is null");
        Assert.isTrue(
                usageType.equals(dataSourceEntity.getUsageType()),
                "datasource usageType mismatch, expected: " + usageType.name());
        Assert.isTrue(
                dataSourceEntity.getStatus() != null
                        && Integer.valueOf(1)
                                .equals(dataSourceEntity.getStatus().getCode()),
                "数据源未启用，不允许绑定");
        assertUsageDatasourceExclusive(tenantId, datasourceId, usageType);

        TenantDataSourceEntity binding = getTenantDataSource(tenantId, usageType);
        if (binding == null) {
            TenantDataSourceEntity entity = new TenantDataSourceEntity();
            entity.setId(IdWorker.getIdStr());
            entity.setTenantId(tenantId);
            entity.setDatasourceId(datasourceId);
            entity.setUsageType(usageType);
            if (FusionConstants.DatabaseUsageType.TENANT.equals(usageType)) {
                entity.setSchemaStatus(TenantDataSourceEntity.SCHEMA_UNINITIALIZED);
            }
            Assert.isTrue(tenantDataSourceMapper.insert(entity) > 0, "save tenant datasource failed");
            return;
        }

        binding.setDatasourceId(datasourceId);
        binding.setUsageType(usageType);
        if (FusionConstants.DatabaseUsageType.TENANT.equals(usageType)) {
            binding.setSchemaStatus(TenantDataSourceEntity.SCHEMA_UNINITIALIZED);
        }
        Assert.isTrue(tenantDataSourceMapper.updateById(binding) > 0, "update tenant datasource failed");
    }

    private void assertUsageDatasourceExclusive(
            String tenantId, String datasourceId, FusionConstants.DatabaseUsageType usageType) {
        if (FusionConstants.DatabaseUsageType.AI.equals(usageType)) {
            TenantDataSourceEntity existingBinding =
                    tenantDataSourceMapper.selectOne(Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                            .eq(TenantDataSourceEntity::getDatasourceId, datasourceId)
                            .eq(TenantDataSourceEntity::getUsageType, usageType)
                            .ne(TenantDataSourceEntity::getTenantId, tenantId)
                            .last("limit 1"));
            Assert.isTrue(existingBinding == null, "AI数据源已被其他租户绑定，必须一租户一数据源");
            return;
        }
        if (FusionConstants.DatabaseUsageType.TENANT.equals(usageType)) {
            TenantDataSourceEntity existingBinding =
                    tenantDataSourceMapper.selectOne(Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                            .eq(TenantDataSourceEntity::getDatasourceId, datasourceId)
                            .and(wrapper -> wrapper.eq(TenantDataSourceEntity::getUsageType, usageType)
                                    .or()
                                    .isNull(TenantDataSourceEntity::getUsageType))
                            .ne(TenantDataSourceEntity::getTenantId, tenantId)
                            .last("limit 1"));
            Assert.isTrue(existingBinding == null, "租户主库数据源已被其他租户绑定，必须一租户一数据源");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTenantDataSourceInitialized(String tenantId) {
        Assert.hasText(tenantId, "tenantId is blank");
        Assert.isTrue(tenantIsolationResolver.isDedicated(tenantId), "仅独立库租户允许初始化主库");
        TenantDataSourceEntity binding = getTenantDataSource(tenantId, FusionConstants.DatabaseUsageType.TENANT);
        Assert.notNull(binding, "tenant datasource binding not found");
        binding.setSchemaStatus(TenantDataSourceEntity.SCHEMA_INITIALIZED);
        Assert.isTrue(tenantDataSourceMapper.updateById(binding) > 0, "update tenant datasource schema status failed");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initTenantDataSource(String tenantId) {
        Assert.hasText(tenantId, "tenantId is blank");
        Assert.isTrue(tenantIsolationResolver.isDedicated(tenantId), "仅独立库租户允许初始化主库");

        TenantDataSourceEntity binding = getTenantDataSource(tenantId, FusionConstants.DatabaseUsageType.TENANT);
        Assert.notNull(binding, "租户未配置数据源");
        Assert.hasText(binding.getDatasourceId(), "租户数据源ID为空");
        Assert.isTrue(
                binding.getSchemaStatus() == null
                        || binding.getSchemaStatus() != TenantDataSourceEntity.SCHEMA_INITIALIZED,
                "租户主库已完成初始化，不可重复执行");

        DataSourceEntity dataSourceEntity = getById(binding.getDatasourceId());
        Assert.notNull(dataSourceEntity, "绑定的数据源不存在");
        Assert.isTrue(dataSourceEntity.getStatus().isOnline(), "数据源未启用，无法初始化");

        RemoteDataSourceService remoteDataSourceService = remoteDataSourceServiceProvider.getIfAvailable();
        Assert.notNull(remoteDataSourceService, "remoteDataSourceService is not available");
        boolean initialized = remoteDataSourceService.initSchema(dataSourceEntity.getId());
        Assert.isTrue(initialized, "租户主库初始化失败");

        markTenantDataSourceInitialized(tenantId);
    }

    private void syncDynamicDataSource(DataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (entity.getStatus() == null
                || !Integer.valueOf(1).equals(entity.getStatus().getCode())) {
            dynamicDataSourceService.removeDataSource(entity.getId());
            return;
        }
        DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(entity);
        boolean updated = dynamicDataSourceService.updateDataSource(entity.getId(), property);
        if (!updated) {
            boolean added = dynamicDataSourceService.addDataSource(property);
            if (!added) {
                throw new IllegalArgumentException("Sync dynamic datasource failed for id: " + entity.getId());
            }
        }
    }

    private void publishAdd(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = buildRemoteDataSource(entity);
        eventPublisher.publishEvent(DataSourceEvent.add(this, remoteDataSource));
    }

    private void publishChange(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = buildRemoteDataSource(entity);
        eventPublisher.publishEvent(DataSourceEvent.update(this, remoteDataSource));
    }

    private void publishRemove(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = buildRemoteDataSource(entity);
        eventPublisher.publishEvent(DataSourceEvent.remove(this, remoteDataSource));
    }

    private RemoteDataSource buildRemoteDataSource(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        if (entity.getUpdatedAt() != null) {
            remoteDataSource.setVersion(entity.getUpdatedAt().toLocalTime().getSecond());
        } else {
            remoteDataSource.setVersion(System.currentTimeMillis());
        }
        return remoteDataSource;
    }
}
