package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
import com.lambda.fusion.datasource.model.QueryTenantDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
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
    private final ObjectMapper objectMapper;

    public TenantDataSourceManageServiceImpl(
            ApplicationEventPublisher eventPublisher,
            DynamicDataSourceService dynamicDataSourceService,
            ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.objectMapper = objectMapper;
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public IPage<TenantDataSourceEntity> page(QueryTenantDataSource queryDTO) {
        return page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UpsertTenantDataSource input) {
        Assert.notNull(input, "input is null");
        TenantDataSourceEntity entity = input.toEntity();
        Assert.isTrue(save(entity), "save failed");
        syncDynamicDataSource(entity);
        publishAdd(entity);
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
            RemoteDataSource remoteDataSource = new RemoteDataSource();
            remoteDataSource.setId(id);
            remoteDataSource.setTenantId(existing.getTenantId());
            eventPublisher.publishEvent(DataSourceEvent.remove(this, remoteDataSource));
        }
        removeById(id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public boolean test(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        RemoteDataSource dto = toDTO(entity);
        DataSourceProperty dataSourceProperty = com.lambda.fusion.datasource.util.DataSourcePropertyUtils.getDataSourceProperty(dto);
        return dynamicDataSourceService.test(dataSourceProperty);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        if (FusionConstants.ENABLED.equals(entity.getEnabled())) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setEnabled(FusionConstants.ENABLED);
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        entity.setEnabled(FusionConstants.DISABLED);
        Assert.isTrue(updateById(entity), "update failed");

        RemoteDataSource remoteDataSource = new RemoteDataSource();
        remoteDataSource.setId(id);
        remoteDataSource.setTenantId(entity.getTenantId());
        eventPublisher.publishEvent(DataSourceEvent.remove(this, remoteDataSource));
    }

    private void publishAdd(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = toDTO(entity);
        eventPublisher.publishEvent(DataSourceEvent.add(this, remoteDataSource));
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

        // 使用工具类统一构建，确保 poolName 被正确设置（路由依赖此字段）
        RemoteDataSource dto = toDTO(entity);
        DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(dto);

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
