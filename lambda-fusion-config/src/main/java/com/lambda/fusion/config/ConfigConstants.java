package com.lambda.fusion.config;

import lombok.experimental.UtilityClass;

/**
 * 配置核心模块常量定义
 * 集中管理所有硬编码常量，提高代码可维护性
 *
 */
@UtilityClass
public final class ConfigConstants {

    /**
     * 数据库相关常量
     */
    public static final class Database {
        private Database() {}

        /**
         * 公共配置模块名称
         */
        public static final String PUBLIC_APPLICATION = "public";
    }

    /**
     * 属性源相关常量
     */
    public static final class PropertySource {
        private PropertySource() {}

        /**
         * 数据库属性源名称
         */
        public static final String DATABASE_PROPERTY_SOURCE_NAME = "DataBaseBasedPropertySource";

        /**
         * 刷新参数属性源名称
         */
        public static final String REFRESH_ARGS_PROPERTY_SOURCE = "refreshArgs";

        /**
         * Spring应用名称属性键
         */
        public static final String SPRING_APPLICATION_NAME = "spring.application.name";
    }

    /**
     * 数据源连接池相关常量
     */
    public static final class DataSource {
        private DataSource() {}

        /**
         * 默认最大连接池大小
         */
        public static final int DEFAULT_MAX_POOL_SIZE = 1;

        /**
         * 默认最小空闲连接数
         */
        public static final int DEFAULT_MIN_IDLE = 1;

        /**
         * 默认连接超时时间（毫秒）
         */
        public static final long DEFAULT_CONNECTION_TIMEOUT = 3000L;

        /**
         * 连接池名称
         */
        public static final String POOL_NAME = "DatabaseBasedPropertySource-Pool";

        /**
         * 独立数据源配置URL属性键
         */
        public static final String CONFIG_DATASOURCE_URL = "lambda.fusion.config.datasource.url";

        /**
         * 独立数据源配置前缀
         */
        public static final String CONFIG_DATASOURCE_PREFIX = "lambda.fusion.config.datasource";
    }

    /**
     * Nacos相关常量
     */
    public static final class Nacos {
        private Nacos() {}

        /**
         * Nacos属性源仓库类名
         */
        public static final String PROPERTY_SOURCE_REPOSITORY_CLASS =
                "com.alibaba.cloud.nacos.NacosPropertySourceRepository";
    }

    /**
     * 配置刷新相关常量
     */
    public static final class Refresh {
        private Refresh() {}

        /**
         * 自动刷新配置属性键
         */
        public static final String AUTO_REFRESH_ENABLED = "lambda.fusion.config.auto-refresh.enabled";

        /**
         * 刷新初始延迟时间（秒）
         */
        public static final int INITIAL_DELAY_SECONDS = 10;

        /**
         * 刷新间隔时间（秒）
         */
        public static final int REFRESH_INTERVAL_SECONDS = 30;

        /**
         * 线程池核心线程数
         */
        public static final int CORE_POOL_SIZE = 1;

        /**
         * 刷新线程名称
         */
        public static final String THREAD_NAME = "DatabaseContextRefresher";
    }

    /**
     * 错误消息常量
     */
    public static final class ErrorMessages {
        private ErrorMessages() {}

        /**
         * 数据源配置未找到警告消息
         */
        public static final String DATASOURCE_CONFIG_NOT_FOUND = "Could not find dataSource configuration, ignored. ";

        /**
         * 获取数据源属性失败警告消息
         */
        public static final String FAILED_TO_GET_DATASOURCE_PROPERTY =
                "Failed to get DataSourceProperty from environment";

        /**
         * 变更检测获取数据源属性失败警告消息
         */
        public static final String FAILED_TO_GET_DATASOURCE_PROPERTY_FOR_CHANGE =
                "Failed to get DataSourceProperty for change detection";

        /**
         * 上下文刷新失败错误消息
         */
        public static final String FAILED_TO_REFRESH_CONTEXT = "Failed to refresh context";
    }

    /**
     * 加密相关常量
     */
    public static final class Encryption {
        private Encryption() {}

        /**
         * AES加密填充方式
         */
        public static final String AES_PADDING = "PKCS7Padding";

        /**
         * 配置加密无密钥错误消息
         */
        public static final String CONFIG_ENCRYPT_NO_KEY = "lambda.fusion.config.encrypt.nokey";

        /**
         * 配置加密安全密钥错误消息
         */
        public static final String CONFIG_ENCRYPT_SECURITY_KEY_ERROR = "lambda.fusion.config.encrypt.secrity.key.error";
    }

    /**
     * 系统配置相关常量
     */
    public static final class SystemConfig {
        private SystemConfig() {}

        /**
         * 服务器验证码启用配置键
         */
        public static final String SERVER_CAPTCHA_ENABLED = "lambda.security.form.verify.enabled";

        /**
         * 授权密码自定义策略配置键
         */
        public static final String AUTHORIZE_PASSWORD_CUSTOMIZE = "lambda.fusion.authorize.password-strategy.customize";

        /**
         * HTTP方法隐藏启用配置键
         */
        public static final String HTTP_METHOD_HIDDEN_ENABLED = "lambda.fusion.web.filter.hiddenmethod.enabled";

        /**
         * 授权密码策略模式配置键
         */
        public static final String AUTHORIZE_PASSWORD_STRATEGY_MODE = "lambda.fusion.authorize.password-strategy.mode";

        /**
         * RSA加密公钥配置键
         */
        public static final String RSA_ENCRYPT_PUBLIC_KEY = "rsa.public-key";

        /**
         * RSA加密私钥配置键
         */
        public static final String RSA_ENCRYPT_PRIVATE_KEY = "rsa.private-key";
    }
}
