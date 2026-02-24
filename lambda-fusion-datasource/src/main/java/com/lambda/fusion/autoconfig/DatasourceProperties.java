package com.lambda.fusion.autoconfig;

import com.lambda.fusion.datasource.DatasourceConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源模块配置属性
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@Data
@ConfigurationProperties(prefix = DatasourceConstants.PREFIX)
public class DatasourceProperties {

    /**
     * 运行模式
     * <p>
     * server: 服务端模式，读取本地数据库，对外提供数据源信息
     * client: 客户端模式，远程订阅数据源信息
     */
    private String mode = DatasourceConstants.DEFAULT_MODE;

    /**
     * Dubbo 相关配置
     */
    private Dubbo dubbo = new Dubbo();

    /**
     * 客户端初始化重试配置（仅 client 模式生效）
     */
    private Retry retry = new Retry();

    /**
     * 租户前缀配置
     * <p>
     * 用于定义不同模块的租户数据源前缀映射
     * key: 模块标识（如 ai, auth, config 等）
     * value: 租户数据源前缀（如 ai_tenant_, auth_tenant_ 等）
     */
    private Map<String, String> tenantPrefixes = new HashMap<>();

    /**
     * 默认租户前缀
     */
    private String defaultTenantPrefix = "tenant_";

    /**
     * 获取指定模块的租户前缀
     *
     * @param module 模块标识
     * @return 租户前缀，如果未配置则返回默认值
     */
    public String getTenantPrefix(String module) {
        return tenantPrefixes.getOrDefault(module, defaultTenantPrefix);
    }

    @Data
    public static class Dubbo {
        /**
         * 服务分组
         */
        private String group = DatasourceConstants.DUBBO_GROUP;

        /**
         * 服务版本
         */
        private String version = DatasourceConstants.DUBBO_VERSION;
    }

    @Data
    public static class Retry {
        /**
         * 最大重试次数（不含首次尝试），0 表示不重试
         */
        private int maxAttempts = 5;

        /**
         * 初始重试等待时间（毫秒）
         */
        private long initialDelay = 5000L;

        /**
         * 退避乘数（指数退避）
         */
        private double multiplier = 2.0;

        /**
         * 最大重试等待时间（毫秒）
         */
        private long maxDelay = 60000L;
    }
}
