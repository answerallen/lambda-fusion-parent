package com.lambda.fusion.datasource.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DynamicDataSourceInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceInitializer.class);

    private static final int ENABLED = 1;

    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceService dynamicDataSourceService;

    public DynamicDataSourceInitializer(DataSourceMapper dataSourceMapper, DynamicDataSourceService dynamicDataSourceService) {
        this.dataSourceMapper = dataSourceMapper;
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DataSourceEntity> enabled = dataSourceMapper.selectList(
                Wrappers.lambdaQuery(DataSourceEntity.class).eq(DataSourceEntity::getEnabled, ENABLED));
        for (DataSourceEntity entity : enabled) {
            boolean added = dynamicDataSourceService.addDataSource(toProperty(entity));
            if (!added) {
                log.warn("Dynamic datasource init failed. id={}", entity.getId());
            }
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
