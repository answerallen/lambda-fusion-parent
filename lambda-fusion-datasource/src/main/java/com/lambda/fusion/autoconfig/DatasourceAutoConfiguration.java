package com.lambda.fusion.autoconfig;

import com.lambda.fusion.datasource.DatasourceConfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 数据源模块自动配置
 *
 * @author jin
 */
@AutoConfiguration
@Import(DatasourceConfigure.class)
public class DatasourceAutoConfiguration {}
