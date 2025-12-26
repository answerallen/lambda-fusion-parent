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

        /**
         * 查询配置的SQL语句
         */
        public static final String SELECT_CONFIGS_SQL =
                "SELECT property_key, property_value, application FROM la_configs WHERE application = ? OR application = 'public'";

        /**
         * 检查配置变更的SQL语句（轻量级）
         * 计算配置的校验和，避免全量查询
         */
        public static final String CHECK_CONFIGS_CHANGED_SQL =
                "SELECT MD5(GROUP_CONCAT(CONCAT(property_key, property_value, application) ORDER BY property_key, application)) " +
                        "FROM la_configs WHERE application = ? OR application = 'public'";
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
        public static final String SERVER_CAPTCHA_ENABLED = "spring.security.form.verify.enabled";

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

    /**
     * 日志消息常量
     */
    public static final class LogMessages {
        private LogMessages() {}

        /**
         * 忽略刷新处理日志消息
         */
        public static final String IGNORING_REFRESH_PROCESSING = "Ignoring refresh processing for environment: {}";

        /**
         * 数据库配置变更日志消息
         */
        public static final String DATABASE_CONFIG_CHANGED =
                "Database config data has been changed! Ready to refresh context..";

        /**
         * 获取刷新锁成功日志消息
         */
        public static final String REFRESH_LOCK_ACQUIRED = "Successfully acquired refresh lock";

        /**
         * 释放刷新锁成功日志消息
         */
        public static final String REFRESH_LOCK_RELEASED = "Successfully released refresh lock";

        /**
         * 上下文刷新完成日志消息
         */
        public static final String CONTEXT_REFRESH_COMPLETED = "Context refresh completed successfully";

        /**
         * 刷新进行中跳过日志消息
         */
        public static final String REFRESH_IN_PROGRESS_SKIP = "Refresh already in progress, skipping this attempt";

        /**
         * 数据源为空无变更检测日志消息
         */
        public static final String DATASOURCE_NULL_NO_CHANGES = "DataSource is null, no changes detected";

        /**
         * 启动日志消息
         */
        public static final String REFRESHER_STARTING_UP = "DatabaseContextRefresher is starting up...";

        /**
         * 调度完成日志消息
         */
        public static final String REFRESHER_SCHEDULED =
                "DatabaseContextRefresher scheduled with {}s initial delay and {}s interval";

        /**
         * 环境变更事件接收日志消息
         */
        public static final String ENVIRONMENT_CHANGE_EVENT_RECEIVED =
                "Environment change event received, context is refreshing!";

        /**
         * 刷新作用域刷新事件接收日志消息
         */
        public static final String REFRESH_SCOPE_EVENT_RECEIVED =
                "Refresh scope refreshed event received, context refresh finished!";

        /**
         * 数据源为空无法创建属性源警告消息
         */
        public static final String DATASOURCE_NULL_CANNOT_CREATE_PROPERTY_SOURCE =
                "DataSource is null, cannot create property source";

        /**
         * 获取连接创建属性源失败警告消息
         */
        public static final String FAILED_TO_GET_CONNECTION_FOR_PROPERTY_SOURCE =
                "Failed to get connection for property source creation";

        /**
         * 创建属性源变更检测失败警告消息
         */
        public static final String FAILED_TO_CREATE_PROPERTY_SOURCE_FOR_CHANGE =
                "Failed to create property source for change detection";

        /**
         * 属性源已更新日志消息
         */
        public static final String PROPERTY_SOURCE_UPDATED = "PropertySource has been updated due to changes";

        /**
         * 数据源关闭成功日志消息
         */
        public static final String DATASOURCE_CLOSED_SUCCESSFULLY = "DataSource closed successfully";

        /**
         * 关闭数据源时发生错误警告消息
         */
        public static final String ERROR_CLOSING_DATASOURCE = "Error occurred while closing DataSource";

        /**
         * 数据库属性源定位器销毁成功日志消息
         */
        public static final String LOCATOR_DESTROYED_SUCCESSFULLY =
                "DatabaseBasedPropertySourceLocator destroyed successfully";

        /**
         * 数据库属性源已重建日志消息
         */
        public static final String DATASOURCE_REBUILT = "DataBaseBasedPropertySource has been rebuilt. {}";

        /**
         * 数据库属性源已初始化日志消息
         */
        public static final String DATASOURCE_INITIALIZED = "DataBaseBasedPropertySource has been initialized. {}";
    }
}
