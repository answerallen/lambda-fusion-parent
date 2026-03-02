package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.event.DataSourceEvent;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressFBWarnings({"EI_EXPOSE_REP2"})
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
    @Transactional(rollbackFor = Exception.class)
    public void save(UpsertDataSource upsertDataSource) {
        Assert.notNull(upsertDataSource, "input is null");
        DataSourceEntity entity = upsertDataSource.toEntity();
        if (entity.getStatus() == null) {
            entity.setStatus(DatasourceConstants.DatasourceStatus.ONLINE);
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
        if (DatasourceConstants.DatasourceStatus.ONLINE == entity.getStatus()) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setStatus(DatasourceConstants.DatasourceStatus.ONLINE);
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

    private void syncDynamicDataSource(DataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (DatasourceConstants.DatasourceStatus.ONLINE != entity.getStatus()) {
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
        RemoteDataSource remoteDataSource = DataSourcePropertyUtils.buildDataSourceEntity(entity);
        remoteDataSource.setTenantId(null);
        if (entity.getUpdatedAt() != null) {
            remoteDataSource.setVersion(entity.getUpdatedAt().toLocalTime().getSecond());
        } else {
            remoteDataSource.setVersion(System.currentTimeMillis());
        }
        return remoteDataSource;
    }
}
