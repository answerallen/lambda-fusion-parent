package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.event.DataSourceEvent;
import com.lambda.fusion.datasource.mapper.TenantDataSourceMapper;
import com.lambda.fusion.datasource.model.QueryTenantDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceService;
import com.lambda.fusion.datasource.tenant.AbstractTenantDataSourceProvisioner;
import com.lambda.fusion.datasource.tenant.TenantIsolationModeResolver;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TenantDataSourceServiceImpl extends ServiceImpl<TenantDataSourceMapper, TenantDataSourceEntity>
        implements TenantDataSourceService {

    private final ApplicationEventPublisher eventPublisher;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final TenantIsolationModeResolver tenantIsolationModeResolver;
    private final List<AbstractTenantDataSourceProvisioner> tenantDataSourceProvisioners;

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
    public void save(UpsertTenantDataSource upsertTenantDataSource) {
        Assert.notNull(upsertTenantDataSource, "input is null");
        TenantDataSourceEntity entity = upsertTenantDataSource.toEntity();
        Assert.isTrue(save(entity), "save failed");
        syncDynamicDataSource(entity);
        publishAdd(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpsertTenantDataSource upsertTenantDataSource) {
        Assert.hasText(id, "id is blank");
        Assert.notNull(upsertTenantDataSource, "input is null");
        TenantDataSourceEntity existing = getById(id);
        Assert.notNull(existing, "entity not found");
        TenantDataSourceEntity entity = upsertTenantDataSource.toEntity();
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
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        DataSourceProperty dataSourceProperty = DataSourcePropertyUtils.getDataSourceProperty(remoteDataSource);
        return dynamicDataSourceService.test(dataSourceProperty);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        if (entity.getEnabled().isEnabled()) {
            syncDynamicDataSource(entity);
            return;
        }
        entity.setEnabled(FusionConstants.ActiveStatus.ENABLED);
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
        entity.setEnabled(FusionConstants.ActiveStatus.DISABLED);
        Assert.isTrue(updateById(entity), "update failed");
        RemoteDataSource remoteDataSource = new RemoteDataSource();
        remoteDataSource.setId(id);
        remoteDataSource.setTenantId(entity.getTenantId());
        eventPublisher.publishEvent(DataSourceEvent.remove(this, remoteDataSource));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSchema(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        initializeTenantSchema(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSchema(String id) {
        Assert.hasText(id, "id is blank");
        TenantDataSourceEntity entity = getById(id);
        Assert.notNull(entity, "entity not found");
        removeTenantDatasource(entity);
    }

    private void publishAdd(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        eventPublisher.publishEvent(DataSourceEvent.add(this, remoteDataSource));
    }

    private void publishChange(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        eventPublisher.publishEvent(DataSourceEvent.update(this, remoteDataSource));
    }

    private void syncDynamicDataSource(TenantDataSourceEntity entity) {
        if (entity == null) {
            return;
        }
        if (entity.getEnabled().isDisabled()) {
            dynamicDataSourceService.removeDataSource(entity.getId());
            return;
        }

        // 使用转换器统一构建 RemoteDataSource
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(remoteDataSource);

        boolean updated = dynamicDataSourceService.updateDataSource(entity.getId(), property);
        if (!updated) {
            boolean added = dynamicDataSourceService.addDataSource(property);
            if (!added) {
                throw new RuntimeException("同步动态数据源失败，ID: " + entity.getId());
            }
        }
    }

    private void initializeTenantSchema(TenantDataSourceEntity entity) {
        if (isProvisioningRequired(entity)) {
            RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
            for (AbstractTenantDataSourceProvisioner provisioner : tenantDataSourceProvisioners) {
                if (provisioner.supports(remoteDataSource)) {
                    provisioner.provisionTenant(entity.getTenantId(), remoteDataSource);
                }
            }
        }
    }

    private void removeTenantDatasource(TenantDataSourceEntity entity) {
        if (isProvisioningRequired(entity)) {
            RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
            for (AbstractTenantDataSourceProvisioner provisioner : tenantDataSourceProvisioners) {
                if (provisioner.supports(remoteDataSource)) {
                    provisioner.deprovisionTenant(entity.getTenantId());
                }
            }
        }
    }

    private boolean isProvisioningRequired(TenantDataSourceEntity entity) {
        if (entity == null || entity.getTenantId() == null) {
            return false;
        }
        if (tenantIsolationModeResolver.isShared(entity.getTenantId())) {
            return false;
        }
        return tenantDataSourceProvisioners != null && !tenantDataSourceProvisioners.isEmpty();
    }
}
