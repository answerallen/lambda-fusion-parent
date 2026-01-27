package com.lambda.fusion.datasource.client;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.api.DataSourceChangeEvent;
import com.lambda.fusion.datasource.api.DataSourceChangeListener;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
public class DataSourceChangeListenerImpl implements DataSourceChangeListener {

    private final DynamicDataSourceService dynamicDataSourceService;

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
                    handleAddOrUpdate(event.getDataSource());
                    break;
                case DELETE:
                case DISABLE:
                    handleDeleteOrDisable(event.getDataSource());
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

    private void handleAddOrUpdate(RemoteDataSource dto) {
        if (dto == null) {
            log.warn("Received ADD/UPDATE/ENABLE event with null DTO");
            return;
        }
        log.info("Adding/Updating datasource: {}", dto.getDatasourceName());
        try {
            DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(dto);
            dynamicDataSourceService.addDataSource(property);
        } catch (Exception e) {
            log.error("Failed to add/update datasource: {}", dto.getDatasourceName(), e);
        }
    }

    private void handleDeleteOrDisable(RemoteDataSource dto) {
        if (dto != null) {
            log.info("Removing/Disabling datasource: {}", dto.getDatasourceName());
            try {
                dynamicDataSourceService.removeDataSource(dto.getDatasourceName());
            } catch (Exception e) {
                log.error("Failed to remove datasource: {}", dto.getDatasourceName(), e);
            }
        } else {
            log.warn("Received DELETE/DISABLE event without DTO info, cannot identify pool name to remove.");
        }
    }
}
