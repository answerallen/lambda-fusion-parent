package com.lambda.fusion.authority;

import cn.dev33.satoken.listener.SaTokenListener;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lambda.cloud.mybatis.handler.EntityMetaFiller;
import com.lambda.cloud.sse.listener.SseEventListener;
import com.lambda.fusion.authority.inteceptor.TenantContextInterceptor;
import com.lambda.fusion.authority.listenner.UserOnlineLogListener;
import com.lambda.fusion.authority.listenner.UserSeeEventListener;
import com.lambda.fusion.authority.manager.TenantManager;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.mapper.UserInfoMapper;
import com.lambda.fusion.authority.mapper.UserMapper;
import com.lambda.fusion.authority.mapper.UserRoleMapper;
import com.lambda.fusion.authority.service.AuthenticationService;
import com.lambda.fusion.authority.service.TenantService;
import com.lambda.fusion.authority.service.UserOnlineLogService;
import com.lambda.fusion.authority.service.UserService;
import com.lambda.fusion.core.api.RemoteAuthenticationService;
import com.lambda.fusion.core.tree.filter.DefaultTreeDataFilter;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import com.lambda.fusion.core.utils.SecurityUtils;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.authority.**.mapper"})
@EnableConfigurationProperties({AuthorityProperties.class})
@ComponentScan(basePackageClasses = AuthorityConfigure.class)
public class AuthorityConfigure implements WebMvcConfigurer {

    public AuthorityConfigure() {
        log.trace("Authority Configuration init");
    }

    private TenantContextInterceptor tenantContextInterceptor;

    @Autowired
    public void setTenantContextInterceptor(@Lazy TenantContextInterceptor tenantContextInterceptor) {
        this.tenantContextInterceptor = tenantContextInterceptor;
    }

    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    public static class DubboServiceConfiguration {
        @Bean
        public ServiceBean<RemoteAuthenticationService> remoteAuthenticationServiceBean(
                AuthenticationService authenticationService, ApplicationContext applicationContext) {
            ServiceBean<RemoteAuthenticationService> serviceBean = new ServiceBean<>(applicationContext);
            serviceBean.setInterface(RemoteAuthenticationService.class);
            serviceBean.setRef(authenticationService);
            return serviceBean;
        }
    }

    @Bean
    public EntityMetaFiller entityMetaFiller() {
        return new EntityMetaFiller() {
            @Override
            public void insertFill(MetaObjectHandler handler, MetaObject metaObject) {
                handler.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                handler.strictInsertFill(
                        metaObject,
                        "createdBy",
                        String.class,
                        SecurityUtils.getUser().getUsername());
            }

            @Override
            public void updateFill(MetaObjectHandler handler, MetaObject metaObject) {
                handler.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                handler.strictUpdateFill(
                        metaObject,
                        "updatedBy",
                        String.class,
                        SecurityUtils.getUser().getUsername());
            }
        };
    }

    @Bean
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(10);
        executor.setCorePoolSize(5);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("OperationLogExecutor-");
        executor.setKeepAliveSeconds(30);
        /*
         * 拒绝处理策略
         * CallerRunsPolicy()：交由调用方线程运行，比如 main 线程。
         * AbortPolicy()：直接抛出异常。
         * DiscardPolicy()：直接丢弃。
         * DiscardOldestPolicy()：丢弃队列中最老的任务。
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean
    @ConditionalOnClass(SaTokenListener.class)
    public SaTokenListener userOnlineLogListener(
            @Autowired(required = false) UserOnlineLogService userOnlineLogService) {
        return new UserOnlineLogListener(userOnlineLogService);
    }

    @Bean
    @ConditionalOnClass(SseEventListener.class)
    public SseEventListener userSeeEventListener(
            @Autowired(required = false) UserOnlineLogService userOnlineLogService) {
        return new UserSeeEventListener(userOnlineLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TreeDataFilter defaultTreeDataFilter() {
        return new DefaultTreeDataFilter();
    }

    @Bean
    @ConditionalOnBean(DataSourceManageService.class)
    public TenantManager tenantManager(
            TenantService tenantService,
            UserService userService,
            UserMapper userMapper,
            UserInfoMapper userInfoMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            RoleMapper roleMapper,
            DataSourceManageService dataSourceManageService) {
        return new TenantManager(
                tenantService,
                userService,
                userMapper,
                userInfoMapper,
                userRoleMapper,
                passwordEncoder,
                roleMapper,
                dataSourceManageService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/**")
                .order(Integer.MIN_VALUE + 100); // 较高优先级执行，确保上下文尽早设置
    }
}
