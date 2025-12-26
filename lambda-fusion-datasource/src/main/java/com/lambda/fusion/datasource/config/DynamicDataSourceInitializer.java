package com.lambda.fusion.datasource.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import java.util.List;

import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        List<DataSourceEntity> enabled = dataSourceMapper.selectList(
                Wrappers.lambdaQuery(DataSourceEntity.class)
                        .eq(DataSourceEntity::getEnabled, Constants.ENABLED));
        enabled.parallelStream().forEach(entity -> {
            DataSourceProperty dataSourceProperty = DataSourcePropertyUtils.getDataSourceProperty(entity);
            boolean added = dynamicDataSourceService.addDataSource(dataSourceProperty);
            if (!added) {
                log.warn("Dynamic datasource init failed. id={}", entity.getId());
            }
        });
    }

}
