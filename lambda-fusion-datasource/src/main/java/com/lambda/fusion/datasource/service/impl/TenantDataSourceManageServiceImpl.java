package com.lambda.fusion.datasource.service.impl;

import static com.lambda.fusion.datasource.service.impl.RemoteDataSourceServiceImpl.getRemoteDataSource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.datasource.event.DataSourceChangeEvent;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TenantDataSourceManageServiceImpl extends ServiceImpl<TenantDataSourceMapper, TenantDataSourceEntity>
        implements TenantDataSourceManageService {

    private final ApplicationEventPublisher eventPublisher;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantDataSourceManageServiceImpl(
            ApplicationEventPublisher eventPublisher, DynamicDataSourceService dynamicDataSourceService) {
        this.eventPublisher = eventPublisher;
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<TenantDataSourceEntity> listAll() {
        return list(Wrappers.lambdaQuery(TenantDataSourceEntity.class).orderByAsc(TenantDataSourceEntity::getId));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public TenantDataSourceEntity get(String id) {
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UpsertTenantDataSource input) {
        Assert.notNull(input, "input is null");
        TenantDataSourceEntity entity = input.toEntity();
        Assert.isTrue(save(entity), "save failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpsertTenantDataSource input) {
        Assert.hasText(id, "id is blank");
        Assert.notNull(input, "input is null");
        TenantDataSourceEntity existing = getById(id);
        Assert.notNull(existing, "entity not found");

        TenantDataSourceEntity entity = input.toEntity();
        entity.setId(id);
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity existing = getById(id);
        if (existing != null) {
            dynamicDataSourceService.removeDataSource(id);
            RemoteDataSource dto = new RemoteDataSource();
            dto.setId(id);
            dto.setTenantId(existing.getTenantId());
            eventPublisher.publishEvent(DataSourceChangeEvent.remove(this, dto));
        }
        removeById(id);
    }

    private void publishChange(TenantDataSourceEntity entity) {
        RemoteDataSource dto = toDTO(entity);
        eventPublisher.publishEvent(DataSourceChangeEvent.update(this, dto));
    }

    private void syncDynamicDataSource(TenantDataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (!Constants.ENABLED.equals(entity.getEnabled())) {
            dynamicDataSourceService.removeDataSource(entity.getId());
            return;
        }

        RemoteDataSource dto = toDTO(entity);
        DataSourceProperty property = new DataSourceProperty();
        property.setId(dto.getId());
        property.setUrl(dto.getJdbcUrl());
        property.setUsername(dto.getUsername());
        property.setPassword(dto.getPassword());
        property.setDriverClassName(dto.getDriverClassName());

        boolean updated = dynamicDataSourceService.updateDataSource(entity.getId(), property);
        if (!updated) {
            boolean added = dynamicDataSourceService.addDataSource(property);
            if (!added) {
                throw new RuntimeException("Sync dynamic datasource failed for id: " + entity.getId());
            }
        }
    }

    private RemoteDataSource toDTO(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = new RemoteDataSource();
        remoteDataSource.setId(entity.getId());
        remoteDataSource.setDatasourceName(entity.getDbName());
        remoteDataSource.setEnabled(entity.getEnabled());
        remoteDataSource.setTenantId(entity.getTenantId());

        // 使用哈希码作为版本号以保持一致性
        remoteDataSource.setVersion(Objects.hash(
                entity.getId(),
                entity.getDbName(),
                entity.getConfiguration(),
                entity.getEnabled(),
                entity.getTenantId()));

        return getRemoteDataSource(entity, remoteDataSource, objectMapper);
    }
}
