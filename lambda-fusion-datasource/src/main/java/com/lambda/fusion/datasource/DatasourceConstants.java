package com.lambda.fusion.datasource;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.dict.DictEnum;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据源常量定义
 */
public interface DatasourceConstants {

    /**
     * 配置前缀
     */
    String PREFIX = "lambda.fusion.datasource";

    /**
     * 运行模式配置键
     */
    String MODE_PROPERTY = PREFIX + ".mode";

    /**
     * 服务端模式：从本地数据库加载数据源配置，并提供Dubbo服务
     */
    String MODE_SERVER = "server";

    /**
     * 客户端模式：从远程Dubbo服务加载数据源配置
     */
    String MODE_CLIENT = "client";

    /**
     * 默认模式
     */
    String DEFAULT_MODE = MODE_SERVER;

    /**
     * Dubbo 服务分组
     */
    String DUBBO_GROUP = "datasource";

    /**
     * Dubbo 服务版本
     */
    String DUBBO_VERSION = "1.0.0";

    @Getter
    @DictMapper(dictName = "DATASOURCE_STATUS", dictUsage = 0, dictDesc = "数据源状态")
    @AllArgsConstructor
    enum DatasourceStatus implements DictEnum<Integer> {
        OFFLINE(0, "下线"),
        ONLINE(1, "在线"),
        READONLY(2, "只读"),
        MAINTENANCE(3, "维护");

        @EnumValue
        @JsonValue
        private final Integer code;

        private final String label;

        public static DatasourceStatus fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (DatasourceStatus status : DatasourceStatus.values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }

        public boolean isOnline() {
            return Objects.equals(code, DatasourceStatus.ONLINE.code);
        }
    }

    /**
     * 变更类型
     */
    enum ChangeType {
        /**
         * 新增
         */
        ADD,
        /**
         * 更新
         */
        UPDATE,
        /**
         * 删除
         */
        DELETE,
        /**
         * 启用
         */
        ENABLE,
        /**
         * 禁用
         */
        DISABLE,
        INIT_SCHEMA,
        REMOVE_SCHEMA
    }
}
