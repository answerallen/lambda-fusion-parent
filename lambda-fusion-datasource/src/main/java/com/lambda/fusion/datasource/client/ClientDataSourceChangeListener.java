package com.lambda.fusion.datasource.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.api.DataSourceChangeEvent;
import com.lambda.fusion.datasource.api.DataSourceChangeListener;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.tenant.TenantSchemaCleaner;
import com.lambda.fusion.datasource.tenant.TenantSchemaInitializer;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源变更回调实现
 * <p>
 * 接收Server端的变更通知，同步到本地动态数据源。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ClientDataSourceChangeListener implements DataSourceChangeListener {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final Optional<TenantSchemaInitializer> schemaInitializer;
    private final Optional<TenantSchemaCleaner> schemaCleaner;

    @Override
    public void onDataSourceChanged(DataSourceChangeEvent event) {
        log.info(
                "Received datasource change event: type={}, dataSourceId={}",
                event.getChangeType(),
                event.getDataSourceId());

        try {
            switch (event.getChangeType()) {
                case ADD:
                case ENABLE:
                case UPDATE:
                    handleAddOrUpdate(event.getDataSourceId(), event.getDataSource());
                    break;
                case DELETE:
                case DISABLE:
                    handleDeleteOrDisable(event.getDataSourceId(), event.getDataSource());
                    break;
                case INIT_SCHEMA:
                    handleInitSchema(event);
                    break;
                case REMOVE_SCHEMA:
                    handleRemoveSchema(event);
                    break;
                default:
                    log.warn("Unknown change type: {}", event.getChangeType());
            }
        } catch (Exception e) {
            log.error(
                    "Failed to handle datasource change event. Type: {}, ID: {}",
                    event.getChangeType(),
                    event.getDataSourceId(),
                    e);
        }
    }

    private void handleAddOrUpdate(String dataSourceId, RemoteDataSource remoteDataSource) {
        if (remoteDataSource == null) {
            log.warn("Received ADD/UPDATE/ENABLE event with null DTO");
            return;
        }
        log.info("Adding/Updating datasource: {}", remoteDataSource.getDatasourceName());
        try {
            String id = ObjectUtil.defaultIfBlank(remoteDataSource.getId(), dataSourceId);
            if (StrUtil.isEmpty(id)) {
                log.warn("Received ADD/UPDATE/ENABLE event without datasource id");
                return;
            }
            DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(remoteDataSource);
            boolean updated = dynamicDataSourceService.updateDataSource(id, property);
            if (!updated) {
                dynamicDataSourceService.addDataSource(property);
            }
        } catch (Exception e) {
            log.error("Failed to add/update datasource: {}", remoteDataSource.getDatasourceName(), e);
        }
    }

    private void handleDeleteOrDisable(String dataSourceId, RemoteDataSource remoteDataSource) {
        String id = ObjectUtil.defaultIfBlank(remoteDataSource.getId(), dataSourceId);
        if (StrUtil.isEmpty(id)) {
            log.warn("Received DELETE/DISABLE event without datasource id");
            return;
        }
        try {
            dynamicDataSourceService.removeDataSource(id);
        } catch (Exception e) {
            log.error("Failed to remove datasource: {}", id, e);
        }
    }

    private void handleInitSchema(DataSourceChangeEvent event) {
        schemaInitializer.ifPresent((initializer) -> {
            String dataSourceId = event.getDataSourceId();
            if (StrUtil.isEmpty(dataSourceId)) {
                log.warn("Received INIT_SCHEMA event without datasource id");
                return;
            }
            try {
                DataSource dataSource = dynamicDataSourceService.getDataSource(dataSourceId);
                initializer.initializeSchema(event.getTenantId(), dataSource);
            } catch (Exception e) {
                log.error("Failed to init schema. datasourceId={}", dataSourceId, e);
            }
        });
    }

    private void handleRemoveSchema(DataSourceChangeEvent event) {
        schemaCleaner.ifPresent(tenantSchemaCleaner -> {
            String dataSourceId = event.getDataSourceId();
            if (StrUtil.isEmpty(dataSourceId)) {
                log.warn("Received REMOVE_SCHEMA event without datasource id");
                return;
            }
            try {
                DataSource dataSource = dynamicDataSourceService.getDataSource(dataSourceId);
                tenantSchemaCleaner.removeSchema(event.getTenantId(), dataSource);
            } catch (Exception e) {
                log.error("Failed to remove schema. datasourceId={}", dataSourceId, e);
            }
        });
    }
}
