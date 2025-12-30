package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.event.DataSourceChangeEvent;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class TenantDataSourceManageServiceImpl extends ServiceImpl<TenantDataSourceMapper, TenantDataSourceEntity>
        implements TenantDataSourceManageService {

    private final ApplicationEventPublisher eventPublisher;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantDataSourceManageServiceImpl(ApplicationEventPublisher eventPublisher, DynamicDataSourceService dynamicDataSourceService) {
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
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity existing = getById(id);
        if (existing != null) {
            RemoteDataSource dto = new RemoteDataSource();
            dto.setId(id);
            dto.setTenantId(existing.getTenantId());
            eventPublisher.publishEvent(DataSourceChangeEvent.remove(this, dto));
        }
        removeById(id);
    }

    private void publishChange(TenantDataSourceEntity entity) {
        try {
            RemoteDataSource dto = toDTO(entity);
            eventPublisher.publishEvent(DataSourceChangeEvent.update(this, dto));
        } catch (Exception e) {
            log.error("Failed to publish change event for tenant datasource {}", entity.getId(), e);
        }
    }

    private RemoteDataSource toDTO(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource
                = new RemoteDataSource();
        remoteDataSource.setId(entity.getId());
        remoteDataSource.setDatasourceName(entity.getDbName());
        remoteDataSource.setEnabled(entity.getEnabled());
        remoteDataSource.setTenantId(entity.getTenantId());
        
        if (entity.getUpdateTime() != null) {
            remoteDataSource.setVersion(entity.getUpdateTime().getTime());
        } else {
            remoteDataSource.setVersion(System.currentTimeMillis());
        }

        try {
            if (StringUtils.hasText(entity.getConfiguration())) {
                JsonNode node = objectMapper.readTree(entity.getConfiguration());
                if (node.has("jdbcUrl")) remoteDataSource.setJdbcUrl(node.get("jdbcUrl").asText());
                else if (node.has("url")) remoteDataSource.setJdbcUrl(node.get("url").asText());
                
                if (node.has("username")) remoteDataSource.setUsername(node.get("username").asText());
                if (node.has("password")) remoteDataSource.setPassword(node.get("password").asText());
                if (node.has("driverClassName")) remoteDataSource.setDriverClassName(node.get("driverClassName").asText());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse configuration for tenant datasource " + entity.getId(), e);
        }

        return remoteDataSource;
    }
}
