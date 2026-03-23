package com.lambda.fusion.datascope;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.datascope.**.mapper"})
@ComponentScan(basePackageClasses = DataScopeConfigure.class)
@EnableConfigurationProperties(DataScopeProperties.class)
public class DataScopeConfigure {}
