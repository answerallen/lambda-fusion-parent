package com.lambda.cloud.scaffold.dict.config;

import com.lambda.fusion.dict.dubbo.RemoteDictService;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DictionaryConfigure
 *
 * @author Jin
 */
@Configuration(proxyBeanMethods = false)
public class DictionaryConfigure {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DubboAutoConfiguration.class)
    public static class DictionaryDubboConfigure {
        @Bean
        @ConditionalOnProperty(prefix = "lambda.fusion.dictionary", value = "enable-dubbo-provider",havingValue = "true")
        public ServiceConfig<RemoteDictService> dictionaryDubboServiceConfig(RemoteDictService remoteDictService) {
            ServiceConfig<RemoteDictService> config = new ServiceConfig<>();
            config.setInterface(RemoteDictService.class);
            config.setRef(remoteDictService);
            return config;
        }
    }

}
