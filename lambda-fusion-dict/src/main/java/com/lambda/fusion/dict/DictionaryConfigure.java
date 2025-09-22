package com.lambda.fusion.dict;

import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
    public static class DictionaryDubboConfigure {}
}
