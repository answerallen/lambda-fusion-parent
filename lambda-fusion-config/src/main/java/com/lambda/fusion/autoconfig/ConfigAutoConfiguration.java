package com.lambda.fusion.autoconfig;

import com.lambda.fusion.config.ConfigConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ConfigConfigure.class)
public class ConfigAutoConfiguration {}
