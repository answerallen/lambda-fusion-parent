package com.lambda.fusion.datasource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.datasource.**.mapper"})
@ComponentScan(basePackageClasses = DatasourceConfigure.class)
public class DatasourceConfigure {}
