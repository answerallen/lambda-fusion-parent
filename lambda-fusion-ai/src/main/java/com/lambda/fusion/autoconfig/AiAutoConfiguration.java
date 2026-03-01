package com.lambda.fusion.autoconfig;

import com.lambda.fusion.ai.AiConfigure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@Slf4j
@AutoConfiguration
@Import(AiConfigure.class)
public class AiAutoConfiguration {}
