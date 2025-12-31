package com.lambda.fusion.datasource.model;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 扩展数据源配置属性，增加连接池名称等字段
 */
@Getter
@Setter
public class DynamicDataSourceProperty extends DataSourceProperty {
    /**
     * 连接池名称
     */
    private String poolName;
}
