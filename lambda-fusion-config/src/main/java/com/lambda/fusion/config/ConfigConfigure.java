package com.lambda.fusion.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.config.**.mapper"})
@ComponentScan(basePackageClasses = ConfigConfigure.class)
public class ConfigConfigure {}
