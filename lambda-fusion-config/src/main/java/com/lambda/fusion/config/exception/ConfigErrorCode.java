package com.lambda.fusion.config.exception;

import com.lambda.cloud.core.exception.model.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConfigErrorCode implements ErrorCode {
    DATASOURCE_CONFIG_NOT_FOUND(14001, "未找到数据源配置"),
    DATASOURCE_PROPERTY_READ_FAILED(14002, "读取数据源配置失败"),
    DATASOURCE_POOL_INIT_FAILED(14003, "初始化连接池失败"),
    DATASOURCE_CONNECTION_FAILED(14004, "获取数据库连接失败"),
    PROPERTY_SOURCE_CREATE_FAILED(14005, "创建配置属性源失败");

    private final Integer code;
    private final String message;
}
