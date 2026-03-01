package com.lambda.fusion.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceServiceImpl;
import com.lambda.fusion.datasource.client.ClientDataSourceInitializer;
import com.lambda.fusion.datasource.client.DataSourceChangeListenerImpl;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.server.ServerDataSourceInitializer;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.tenant.TenantIsolationModeResolver;
import com.lambda.fusion.datasource.tenant.TenantSchemaCleaner;
import com.lambda.fusion.datasource.tenant.TenantSchemaInitializer;
import java.util.List;
import javax.sql.DataSource;
import org.apache.dubbo.config.spring.ServiceBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
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
    public RemoteDataSourceService remoteDataSourceService(
            DataSourceManageService dataSourceManageService,
            DataSourceChangeDispatcher callbackManager,
            ObjectMapper objectMapper,
            TenantIsolationModeResolver tenantIsolationModeResolver) {
        return new RemoteDataSourceServiceImpl(
                dataSourceManageService,
                callbackManager,
                objectMapper,
                tenantIsolationModeResolver);
    }

    @Bean
    @ConditionalOnProperty(
            name = DatasourceConstants.MODE_PROPERTY,
            havingValue = DatasourceConstants.MODE_SERVER,
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
    public DataSourceChangeListenerImpl dataSourceChangeListener(
            DynamicDataSourceService dynamicDataSourceService,
            DataSource dataSource,
            @Autowired(required = false) List<TenantSchemaInitializer> schemaInitializers,
            @Autowired(required = false) List<TenantSchemaCleaner> schemaCleaners) {
        return new DataSourceChangeListenerImpl(
                dynamicDataSourceService,
                dataSource,
                schemaInitializers == null ? List.of() : schemaInitializers,
                schemaCleaners == null ? List.of() : schemaCleaners);
    }

    @Bean
    @ConditionalOnProperty(name = DatasourceConstants.MODE_PROPERTY, havingValue = DatasourceConstants.MODE_CLIENT)
    public ClientDataSourceInitializer clientDataSourceInitializer(
            DynamicDataSourceService dynamicDataSourceService,
            DataSourceChangeListenerImpl dataSourceChangeListener,
            DatasourceProperties datasourceProperties) {
        return new ClientDataSourceInitializer(
                dynamicDataSourceService, dataSourceChangeListener, datasourceProperties);
    }
}
