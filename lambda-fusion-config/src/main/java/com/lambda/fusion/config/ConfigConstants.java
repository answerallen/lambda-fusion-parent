package com.lambda.fusion.config;

import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.dict.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 配置核心模块常量定义
 * 集中管理所有硬编码常量，提高代码可维护性
 *
 */
@SuppressWarnings("unused")
public interface ConfigConstants {

    /**
     * 数据库相关常量
     */
    interface Database {

        /**
         * 公共配置模块名称
         */
        String PUBLIC_APPLICATION = "public";
    }

    /**
     * 属性源相关常量
     */
    interface PropertySource {

        /**
         * 数据库属性源名称
         */
        String DATABASE_PROPERTY_SOURCE_NAME = "DataBaseBasedPropertySource";

        /**
         * 刷新参数属性源名称
         */
        String REFRESH_ARGS_PROPERTY_SOURCE = "refreshArgs";

        /**
         * Spring应用名称属性键
         */
        String SPRING_APPLICATION_NAME = "spring.application.name";
    }

    /**
     * 数据源连接池相关常量
     */
    interface DataSource {

        /**
         * 默认最大连接池大小
         */
        int DEFAULT_MAX_POOL_SIZE = 1;

        /**
         * 默认最小空闲连接数
         */
        int DEFAULT_MIN_IDLE = 1;

        /**
         * 默认连接超时时间（毫秒）
         */
        long DEFAULT_CONNECTION_TIMEOUT = 3000L;

        /**
         * 连接池名称
         */
        String POOL_NAME = "DatabaseBasedPropertySource-Pool";

        /**
         * 独立数据源配置URL属性键
         */
        String CONFIG_DATASOURCE_URL = "lambda.fusion.config.datasource.url";

        /**
         * 独立数据源配置前缀
         */
        String CONFIG_DATASOURCE_PREFIX = "lambda.fusion.config.datasource";
    }

    /**
     * Nacos相关常量
     */
    interface Nacos {

        /**
         * Nacos属性源仓库类名
         */
        String PROPERTY_SOURCE_REPOSITORY_CLASS = "com.alibaba.cloud.nacos.NacosPropertySourceRepository";
    }

    /**
     * 配置刷新相关常量
     */
    interface Refresh {

        /**
         * 自动刷新配置属性键
         */
        String AUTO_REFRESH_ENABLED = "lambda.fusion.config.auto-refresh.enabled";

        /**
         * 刷新初始延迟时间（秒）
         */
        int INITIAL_DELAY_SECONDS = 10;

        /**
         * 刷新间隔时间（秒）
         */
        int REFRESH_INTERVAL_SECONDS = 30;

        /**
         * 线程池核心线程数
         */
        int CORE_POOL_SIZE = 1;

        /**
         * 刷新线程名称
         */
        String THREAD_NAME = "DatabaseContextRefresher";
    }

    /**
     * 加密相关常量
     */
    interface Encryption {

        /**
         * AES加密填充方式
         */
        String AES_PADDING = "PKCS7Padding";
    }

    /**
     * 系统配置相关常量
     */
    interface SystemConfig {
        /**
         * RSA加密公钥配置键
         */
        String RSA_ENCRYPT_PUBLIC_KEY = "rsa.public-key";

        /**
         * RSA加密私钥配置键
         */
        String RSA_ENCRYPT_PRIVATE_KEY = "rsa.private-key";
    }

    @Getter
    @DictMapper(dictName = "CONFIG_TYPE", dictUsage = 0, dictDesc = "配置类型")
    @AllArgsConstructor
    enum RoleType implements DictEnum<Integer> {
        BOOLEAN(1, "布尔开关"),

        ENUM(2, "枚举选择"),

        STRING(3, "字符串"),

        NUMBER(4, "数值类型");

        private final Integer code;
        private final String label;
    }
}
