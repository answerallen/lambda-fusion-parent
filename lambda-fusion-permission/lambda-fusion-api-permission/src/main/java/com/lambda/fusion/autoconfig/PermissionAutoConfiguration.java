package com.lambda.fusion.autoconfig;

import com.lambda.fusion.permission.PermissionConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(PermissionConfigure.class)
public class PermissionAutoConfiguration {}
