package com.lambda.fusion.dict;

import com.lambda.fusion.core.tree.filter.DefaultTreeDataFilter;
import com.lambda.fusion.core.tree.filter.TreeDataFilter;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DictProperties.class)
@MapperScan(basePackages = {"com.lambda.fusion.dict.**.mapper"})
@ComponentScan(basePackageClasses = DictConfigure.class)
public class DictConfigure {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DubboAutoConfiguration.class)
    public static class DictionaryDubboConfigure {}

    @Bean
    @ConditionalOnMissingBean
    public TreeDataFilter defaultTreeDataFilter() {
        return new DefaultTreeDataFilter();
    }
}
