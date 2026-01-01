package com.lambda.fusion.datasource;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.client.ClientDataSourceInitializer;
import com.lambda.fusion.datasource.client.DataSourceChangeListenerImpl;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.server.ServerDataSourceInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 数据源模块自动配置
 */
@Configuration
@EnableConfigurationProperties(DatasourceProperties.class)
@ComponentScan(basePackageClasses = DatasourceConfigure.class)
public class DatasourceConfigure {


    @Bean
    @ConditionalOnProperty(name = DatasourceConstant.MODE_PROPERTY, havingValue = DatasourceConstant.MODE_SERVER, matchIfMissing = true)
    public ServerDataSourceInitializer serverDataSourceInitializer(DataSourceMapper dataSourceMapper,
                                                                 DynamicDataSourceService dynamicDataSourceService) {
        return new ServerDataSourceInitializer(dataSourceMapper, dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstant.MODE_PROPERTY, havingValue = DatasourceConstant.MODE_CLIENT)
    public DataSourceChangeListenerImpl dataSourceChangeListener(DynamicDataSourceService dynamicDataSourceService) {
        return new DataSourceChangeListenerImpl(dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstant.MODE_PROPERTY, havingValue = DatasourceConstant.MODE_CLIENT)
    public ClientDataSourceInitializer clientDataSourceInitializer(DynamicDataSourceService dynamicDataSourceService,
                                                                 DataSourceChangeListenerImpl dataSourceChangeListener) {
        return new ClientDataSourceInitializer(dynamicDataSourceService, dataSourceChangeListener);
    }
}
