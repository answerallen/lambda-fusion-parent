package com.lambda.fusion.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.DatasourceConfigure;
import com.lambda.fusion.datasource.DatasourceConstant;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceServiceImpl;
import com.lambda.fusion.datasource.client.ClientDataSourceInitializer;
import com.lambda.fusion.datasource.client.DataSourceChangeListenerImpl;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.server.ServerDataSourceInitializer;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 数据源模块自动配置
 *
 * @author jin
 */
@AutoConfiguration
@Import(DatasourceConfigure.class)
@EnableConfigurationProperties(DatasourceProperties.class)
public class DatasourceAutoConfiguration {

    @Bean
    public DataSourceChangeDispatcher dataSourceCallbackManager() {
        return new DataSourceChangeDispatcher();
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstant.MODE_PROPERTY,
            havingValue = DatasourceConstant.MODE_SERVER,
            matchIfMissing = true)
    public RemoteDataSourceService remoteDataSourceService(
            DataSourceManageService dataSourceManageService,
            TenantDataSourceManageService tenantDataSourceManageService,
            DataSourceChangeDispatcher callbackManager,
            ObjectMapper objectMapper) {
        return new RemoteDataSourceServiceImpl(
                dataSourceManageService, tenantDataSourceManageService, callbackManager, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstant.MODE_PROPERTY,
            havingValue = DatasourceConstant.MODE_SERVER,
            matchIfMissing = true)
    public ServiceBean<RemoteDataSourceService> remoteDataSourceServiceBean(
            RemoteDataSourceService remoteDataSourceService,
            DatasourceProperties datasourceProperties,
            ApplicationContext applicationContext) {
        ServiceBean<RemoteDataSourceService> serviceBean = new ServiceBean<>(applicationContext);
        serviceBean.setInterface(RemoteDataSourceService.class);
        serviceBean.setRef(remoteDataSourceService);
        serviceBean.setGroup(datasourceProperties.getDubbo().getGroup());
        serviceBean.setVersion(datasourceProperties.getDubbo().getVersion());
        return serviceBean;
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstant.MODE_PROPERTY,
            havingValue = DatasourceConstant.MODE_SERVER,
            matchIfMissing = true)
    public ServerDataSourceInitializer serverDataSourceInitializer(
            DataSourceMapper dataSourceMapper,
            @Autowired(required = false) DynamicDataSourceService dynamicDataSourceService) {
        return new ServerDataSourceInitializer(dataSourceMapper, dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstant.MODE_PROPERTY, havingValue = DatasourceConstant.MODE_CLIENT)
    public DataSourceChangeListenerImpl dataSourceChangeListener(DynamicDataSourceService dynamicDataSourceService) {
        return new DataSourceChangeListenerImpl(dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstant.MODE_PROPERTY, havingValue = DatasourceConstant.MODE_CLIENT)
    public ClientDataSourceInitializer clientDataSourceInitializer(
            DynamicDataSourceService dynamicDataSourceService, DataSourceChangeListenerImpl dataSourceChangeListener) {
        return new ClientDataSourceInitializer(dynamicDataSourceService, dataSourceChangeListener);
    }
}
