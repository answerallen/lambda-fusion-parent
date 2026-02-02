package com.lambda.fusion.authority;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.authority.**.mapper"})
@ComponentScan(basePackageClasses = AuthorityConfigure.class)
public class AuthorityConfigure {}
