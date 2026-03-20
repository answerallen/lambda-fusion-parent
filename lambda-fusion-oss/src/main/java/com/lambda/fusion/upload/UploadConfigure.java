package com.lambda.fusion.upload;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.upload.**.mapper"})
@ComponentScan(basePackageClasses = UploadConfigure.class)
public class UploadConfigure {}
