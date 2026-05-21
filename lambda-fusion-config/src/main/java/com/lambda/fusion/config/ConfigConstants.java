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

    // ========= 常量字段 =========

    /**
     * 公共配置模块名称
     */
    String PUBLIC_APPLICATION = "public";

    /**
     * 开启自动刷新
     */
    String AUTO_REFRESH_ENABLED = "lambda.fusion.config.auto-refresh.enabled";

    /**
     * 数据库属性源名称
     */
    String DATABASE_PROPERTY_SOURCE_NAME = "DataBaseBasedPropertySource";

    /**
     * 连接池名称
     */
    String POOL_NAME = "DatabaseBasedPropertySource-Pool";

    /**
     * 独立数据源配置 URL 属性键
     */
    String CONFIG_DATASOURCE_URL = "lambda.fusion.config.datasource.url";

    /**
     * 独立数据源配置前缀
     */
    String CONFIG_DATASOURCE_PREFIX = "lambda.fusion.config.datasource";

    /**
     * 刷新线程名称
     */
    String THREAD_NAME = "DatabaseContextRefresher";

    /**
     * 网关动态路由配置键
     */
    String GATEWAY_DYNAMIC_ROUTES_KEY = "lambda.gateway.dynamic-routes";

    /**
     * Nacos 属性源仓库类名
     */
    String PROPERTY_SOURCE_REPOSITORY_CLASS = "com.alibaba.cloud.nacos.NacosPropertySourceRepository";

    // ========= 枚举 =========

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
