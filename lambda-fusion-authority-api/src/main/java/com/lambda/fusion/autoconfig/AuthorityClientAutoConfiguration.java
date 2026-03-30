package com.lambda.fusion.autoconfig;

import cn.dev33.satoken.stp.StpInterface;
import com.lambda.fusion.authority.api.RemoteAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证服务客户端自动配置类
 *
 * @author zx
 */
@AutoConfiguration
public class AuthorityClientAutoConfiguration {

    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ReferenceBean")
    public static class DubboServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean(RemoteAuthenticationService.class)
        public ReferenceBean<RemoteAuthenticationService> remoteAuthenticationServiceBean() {
            ReferenceBean<RemoteAuthenticationService> referenceBean = new ReferenceBean<>();
            referenceBean.setInterfaceClass(RemoteAuthenticationService.class);
            return referenceBean;
        }
    }

    @Bean
    @ConditionalOnMissingBean({RemoteAuthenticationService.class})
    public StpInterface remoteUserDetailService(RemoteAuthenticationService remoteAuthenticationService) {
        return remoteAuthenticationService;
    }
}
