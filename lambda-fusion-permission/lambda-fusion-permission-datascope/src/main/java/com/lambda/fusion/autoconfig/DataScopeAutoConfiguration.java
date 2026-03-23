package com.lambda.fusion.autoconfig;

import com.lambda.fusion.datascope.DataScopeConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(DataScopeConfigure.class)
public class DataScopeAutoConfiguration {}
