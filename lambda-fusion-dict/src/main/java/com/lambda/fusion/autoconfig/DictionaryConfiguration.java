package com.lambda.fusion.autoconfig;

import com.lambda.fusion.core.tree.filter.DefaultTreeDataFilter;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * DictionaryConfigure
 *
 * @author Jin
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.lambda.fusion.dict")
@Configuration(proxyBeanMethods = false)
public class DictionaryConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DubboAutoConfiguration.class)
    public static class DictionaryDubboConfigure {}

    @Bean
    @ConditionalOnMissingBean
    public TreeDataFilter defaultTreeDataFilter() {
        return new DefaultTreeDataFilter();
    }
}
