package com.lambda.fusion.datasource.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicDataSourceInitializer implements ApplicationRunner {

    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceService dynamicDataSourceService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting dynamic datasource initialization...");
        long current = 1;
        long size = 50;
        long successCount = 0;
        long errorCount = 0;

        while (true) {
            Page<DataSourceEntity> page = dataSourceMapper.selectPage(
                    new Page<>(current, size),
                    Wrappers.lambdaQuery(DataSourceEntity.class)
                            .eq(DataSourceEntity::getEnabled, Constants.ENABLED)
                            .orderByAsc(DataSourceEntity::getId));

            List<DataSourceEntity> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }

            for (DataSourceEntity entity : records) {
                try {
                    DataSourceProperty dataSourceProperty = DataSourcePropertyUtils.getDataSourceProperty(entity);
                    boolean added = dynamicDataSourceService.addDataSource(dataSourceProperty);
                    if (!added) {
                        log.warn(
                                "Dynamic datasource init failed (addDataSource returned false). id={}", entity.getId());
                        errorCount++;
                    } else {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("Dynamic datasource init error. id={}", entity.getId(), e);
                    errorCount++;
                }
            }

            if (current >= page.getPages()) {
                break;
            }
            current++;
        }
        log.info("Dynamic datasource initialization finished. Success: {}, Error: {}", successCount, errorCount);
    }
}
