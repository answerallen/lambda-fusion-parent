package com.lambda.fusion.autoconfig;

import com.lambda.fusion.core.tree.filter.DefaultTreeDataFilter;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import com.lambda.fusion.dict.DictConfigure;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * DictionaryConfigure
 *
 * @author Jin
 */
@AutoConfiguration
@Import(DictConfigure.class)
@Configuration(proxyBeanMethods = false)
public class DictionaryAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DubboAutoConfiguration.class)
    public static class DictionaryDubboConfigure {}

    @Bean
    @ConditionalOnMissingBean
    public TreeDataFilter defaultTreeDataFilter() {
        return new DefaultTreeDataFilter();
    }
}
