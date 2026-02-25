package com.lambda.fusion.dict;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.dict.**.mapper"})
@ComponentScan(basePackageClasses = DictConfigure.class)
public class DictConfigure {}
