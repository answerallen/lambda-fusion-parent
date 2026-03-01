package com.lambda.fusion.autoconfig;

import com.lambda.autoconfig.SecurityAutoConfiguration;
import com.lambda.fusion.authority.AuthorityConfigure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@Import(AuthorityConfigure.class)
public class AuthorityAutoConfiguration {}
