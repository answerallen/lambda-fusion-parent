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
import com.lambda.fusion.datasource.event.DataSourceEvent;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.tenant.TenantIsolationResolver;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
public class DataSourceManageServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceManageService {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantDataSourceMapper tenantDataSourceMapper;
    private final TenantIsolationResolver tenantIsolationResolver;

    public DataSourceManageServiceImpl(
            DynamicDataSourceService dynamicDataSourceService,
            ApplicationEventPublisher eventPublisher,
            TenantDataSourceMapper tenantDataSourceMapper,
            TenantIsolationResolver tenantIsolationResolver) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.eventPublisher = eventPublisher;
        this.tenantDataSourceMapper = tenantDataSourceMapper;
        this.tenantIsolationResolver = tenantIsolationResolver;
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
        DataSourceEntity entity = upsertDataSource.toEntity();
        entity.setId(id);
        boolean updated = updateById(entity);
        Assert.isTrue(updated, "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
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
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        if (entity.getStatus() != null
                && Integer.valueOf(1).equals(entity.getStatus().getCode())) {
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
