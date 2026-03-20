package com.lambda.fusion.config.commons.exception;

public class ConfigLoadException extends ConfigBusinessException {
    public ConfigLoadException(ConfigErrorCode errorCode) {
        super(errorCode);
    }

    public ConfigLoadException(ConfigErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static ConfigLoadException dataSourceConfigNotFound() {
        return new ConfigLoadException(ConfigErrorCode.DATASOURCE_CONFIG_NOT_FOUND);
    }

    public static ConfigLoadException dataSourcePropertyReadFailed(Throwable cause) {
        return new ConfigLoadException(ConfigErrorCode.DATASOURCE_PROPERTY_READ_FAILED, cause);
    }

    public static ConfigLoadException dataSourcePoolInitFailed(Throwable cause) {
        return new ConfigLoadException(ConfigErrorCode.DATASOURCE_POOL_INIT_FAILED, cause);
    }

    public static ConfigLoadException dataSourceConnectionFailed(Throwable cause) {
        return new ConfigLoadException(ConfigErrorCode.DATASOURCE_CONNECTION_FAILED, cause);
    }

    public static ConfigLoadException propertySourceCreateFailed(Throwable cause) {
        return new ConfigLoadException(ConfigErrorCode.PROPERTY_SOURCE_CREATE_FAILED, cause);
    }
}
