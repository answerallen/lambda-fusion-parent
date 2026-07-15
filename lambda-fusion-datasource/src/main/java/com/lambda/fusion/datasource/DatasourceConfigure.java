package com.lambda.fusion.datasource;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceApi;
import com.lambda.fusion.datasource.client.ClientDataSourceChangeListener;
import com.lambda.fusion.datasource.client.ClientDataSourceInitializer;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.server.ServerDataSourceInitializer;
import com.lambda.fusion.datasource.server.ServerDataSourceService;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ServiceBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.datasource.**.mapper"})
@ComponentScan(basePackageClasses = DatasourceConfigure.class)
@EnableConfigurationProperties(DatasourceProperties.class)
public class DatasourceConfigure {

    @Bean
    public DataSourceChangeDispatcher dataSourceCallbackManager() {
        return new DataSourceChangeDispatcher();
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstants.MODE_PROPERTY,
            havingValue = DatasourceConstants.MODE_SERVER,
            matchIfMissing = true)
    public RemoteDataSourceApi remoteDataSourceService(
            DataSourceManageService dataSourceManageService, DataSourceChangeDispatcher callbackManager) {
        return new ServerDataSourceService(dataSourceManageService, callbackManager);
    }

    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    public static class DubboServiceConfiguration {

        @Bean
        @ConditionalOnClass(ServiceBean.class)
        @ConditionalOnProperty(
                name = DatasourceConstants.MODE_PROPERTY,
                havingValue = DatasourceConstants.MODE_SERVER,
                matchIfMissing = true)
        public ServiceBean<RemoteDataSourceApi> remoteDataSourceServiceBean(
                RemoteDataSourceApi remoteDataSourceApi,
                DatasourceProperties datasourceProperties,
                ApplicationContext applicationContext) {
            ServiceBean<RemoteDataSourceApi> serviceBean = new ServiceBean<>(applicationContext);
            serviceBean.setInterface(RemoteDataSourceApi.class);
            serviceBean.setRef(remoteDataSourceApi);
            serviceBean.setGroup(datasourceProperties.getDubbo().getGroup());
            serviceBean.setVersion(datasourceProperties.getDubbo().getVersion());
            return serviceBean;
        }
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstants.MODE_PROPERTY,
            havingValue = DatasourceConstants.MODE_SERVER,
            matchIfMissing = true)
    public ServerDataSourceInitializer serverDataSourceInitializer(
            DataSourceMapper dataSourceMapper,
            @Autowired(required = false) DynamicDataSourceService dynamicDataSourceService) {
        return new ServerDataSourceInitializer(dataSourceMapper, dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstants.MODE_PROPERTY, havingValue = DatasourceConstants.MODE_CLIENT)
    public ClientDataSourceChangeListener dataSourceChangeListener(DynamicDataSourceService dynamicDataSourceService) {
        return new ClientDataSourceChangeListener(dynamicDataSourceService);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstants.MODE_PROPERTY, havingValue = DatasourceConstants.MODE_CLIENT)
    public ClientDataSourceInitializer clientDataSourceInitializer(
            DynamicDataSourceService dynamicDataSourceService,
            ClientDataSourceChangeListener dataSourceChangeListener,
            DatasourceProperties datasourceProperties) {
        return new ClientDataSourceInitializer(
                dynamicDataSourceService, dataSourceChangeListener, datasourceProperties);
    }
}
