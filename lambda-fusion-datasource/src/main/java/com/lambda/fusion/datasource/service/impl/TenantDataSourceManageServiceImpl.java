package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.api.RemoteDataSourceServiceImpl;
import com.lambda.fusion.datasource.event.DataSourceEvent;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
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
            RemoteDataSource remoteDataSource = new RemoteDataSource();
            remoteDataSource.setId(id);
            remoteDataSource.setTenantId(existing.getTenantId());
            eventPublisher.publishEvent(DataSourceEvent.remove(this, remoteDataSource));
        }
        removeById(id);
    }

    private void publishChange(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = toDTO(entity);
        eventPublisher.publishEvent(DataSourceEvent.update(this, remoteDataSource));
    }

    private void syncDynamicDataSource(TenantDataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (!FusionConstants.ENABLED.equals(entity.getEnabled())) {
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
        remoteDataSource.setDbType(entity.getDbType());

        // 解析配置信息
        try {
            if (entity.getConfiguration() != null && !entity.getConfiguration().isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(entity.getConfiguration());
                RemoteDataSourceServiceImpl.validAndSet(remoteDataSource, node);
            }
        } catch (Exception e) {
            log.error("Failed to parse tenant configuration for datasource: {}", entity.getDbName(), e);
        }

        // 使用哈希码作为版本号以保持一致性
        remoteDataSource.setVersion(Objects.hash(
                entity.getId(),
                entity.getDbName(),
                entity.getConfiguration(),
                entity.getEnabled(),
                entity.getTenantId()));

        return remoteDataSource;
    }
}
