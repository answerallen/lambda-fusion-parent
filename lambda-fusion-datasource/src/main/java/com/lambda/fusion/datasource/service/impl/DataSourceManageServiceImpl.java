package com.lambda.fusion.datasource.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.datasource.event.DataSourceEvent;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceManageServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceManageService {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final ApplicationEventPublisher eventPublisher;

    public DataSourceManageServiceImpl(
            DynamicDataSourceService dynamicDataSourceService, ApplicationEventPublisher eventPublisher) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.eventPublisher = eventPublisher;
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public DataSourceEntity get(String id) {
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UpsertDataSource input) {
        Assert.notNull(input, "input is null");
        DataSourceEntity entity = input.toEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        Assert.isTrue(save(entity), "save failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpsertDataSource input) {
        Assert.hasText(id, "id is blank");
        Assert.notNull(input, "input is null");

        DataSourceEntity existing = getById(id);
        Assert.notNull(existing, "entity not found");

        DataSourceEntity entity = input.toEntity();
        entity.setId(id);
        if (entity.getPassword() == null || entity.getPassword().isEmpty()) {
            entity.setPassword(existing.getPassword());
        }
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
        publishChange(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity existing = getById(id);
        if (existing != null) {
            dynamicDataSourceService.removeDataSource(id);
            // 构建用于删除的 DTO（包含 Name 以便客户端移除）
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
        if (Constants.ENABLED.equals(entity.getEnabled())) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setEnabled(Constants.ENABLED);
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
        entity.setEnabled(Constants.DISABLED);
        Assert.isTrue(updateById(entity), "update failed");
        dynamicDataSourceService.removeDataSource(id);
        // 如果已禁用，客户端不应使用它。
        publishChange(entity);
    }

    private void syncDynamicDataSource(DataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (!Constants.ENABLED.equals(entity.getEnabled())) {
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

    private void publishChange(DataSourceEntity entity) {
        RemoteDataSource dto = new RemoteDataSource();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDatasourceName());
        dto.setDriverClassName(entity.getDriverClassName());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setPassword(entity.getPassword());
        dto.setEnabled(entity.getEnabled());
        dto.setTenantId(null); // 全局共享
        // 由于没有 updateTime，使用哈希码作为版本号
        dto.setVersion(Objects.hash(
                entity.getId(),
                entity.getDatasourceName(),
                entity.getDriverClassName(),
                entity.getJdbcUrl(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getEnabled()));

        eventPublisher.publishEvent(DataSourceEvent.update(this, dto));
    }
}
