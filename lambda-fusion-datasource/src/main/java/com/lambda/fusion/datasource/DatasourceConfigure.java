package com.lambda.fusion.datasource;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.api.RemoteDataSourceServiceImpl;
import com.lambda.fusion.datasource.client.ClientDataSourceChangeListener;
import com.lambda.fusion.datasource.client.ClientDataSourceInitializer;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.interceptor.TenantDataSourceInterceptor;
import com.lambda.fusion.datasource.mapper.DataSourceMapper;
import com.lambda.fusion.datasource.server.ServerDataSourceInitializer;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.tenant.TenantSchemaCleaner;
import com.lambda.fusion.datasource.tenant.TenantSchemaInitializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ServiceBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.datasource.**.mapper"})
@ComponentScan(basePackageClasses = DatasourceConfigure.class)
@EnableConfigurationProperties(DatasourceProperties.class)
public class DatasourceConfigure implements WebMvcConfigurer {

    private TenantDataSourceInterceptor tenantDataSourceInterceptor;

    @Autowired
    public void setTenantDataSourceInterceptor(TenantDataSourceInterceptor tenantDataSourceInterceptor) {
        this.tenantDataSourceInterceptor = tenantDataSourceInterceptor;
    }

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
            DataSourceManageService dataSourceManageService, DataSourceChangeDispatcher callbackManager) {
        return new RemoteDataSourceServiceImpl(dataSourceManageService, callbackManager);
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
    public ClientDataSourceChangeListener dataSourceChangeListener(
            DynamicDataSourceService dynamicDataSourceService,
            ObjectProvider<TenantSchemaInitializer> initializerObjectProvider,
            ObjectProvider<TenantSchemaCleaner> cleanerObjectProvider) {
        return new ClientDataSourceChangeListener(
                dynamicDataSourceService, initializerObjectProvider, cleanerObjectProvider);
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantDataSourceInterceptor)
                .addPathPatterns("/**")
                // 确保执行顺序在 Core 层的 TenantContextInterceptor 之后
                .order(Integer.MIN_VALUE + 200);
    }
}
