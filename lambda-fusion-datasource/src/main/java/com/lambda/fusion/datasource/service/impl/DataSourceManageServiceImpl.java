package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceManageServiceImpl extends ServiceImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceManageService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final DynamicDataSourceService dynamicDataSourceService;

    public DataSourceManageServiceImpl(DynamicDataSourceService dynamicDataSourceService) {
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<DataSourceEntity> listAll() {
        return list(Wrappers.lambdaQuery(DataSourceEntity.class).orderByAsc(DataSourceEntity::getId));
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
        Assert.isTrue(save(entity), "save failed");
        syncDynamicDataSource(entity);
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
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity existing = getById(id);
        if (existing != null) {
            dynamicDataSourceService.removeDataSource(id);
        }
        removeById(id);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public boolean test(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        return dynamicDataSourceService.test(toProperty(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        if (Integer.valueOf(ENABLED).equals(entity.getEnabled())) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setEnabled(ENABLED);
        Assert.isTrue(updateById(entity), "update failed");
        syncDynamicDataSource(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        Assert.hasText(id, "id is blank");
        DataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        entity.setEnabled(DISABLED);
        Assert.isTrue(updateById(entity), "update failed");
        dynamicDataSourceService.removeDataSource(id);
    }

    private void syncDynamicDataSource(DataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (!Integer.valueOf(ENABLED).equals(entity.getEnabled())) {
            dynamicDataSourceService.removeDataSource(entity.getId());
            return;
        }
        DataSourceProperty property = toProperty(entity);
        boolean updated = dynamicDataSourceService.updateDataSource(entity.getId(), property);
        if (!updated) {
            dynamicDataSourceService.addDataSource(property);
        }
    }

    private DataSourceProperty toProperty(DataSourceEntity entity) {
        DataSourceProperty property = new DataSourceProperty();
        property.setId(entity.getId());
        property.setUrl(entity.getJdbcUrl());
        property.setUsername(entity.getUsername());
        property.setPassword(entity.getPassword());
        property.setDriverClassName(entity.getDriverClassName());
        return property;
    }
}
