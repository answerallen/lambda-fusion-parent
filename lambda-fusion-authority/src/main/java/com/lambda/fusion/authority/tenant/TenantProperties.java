package com.lambda.fusion.authority.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户相关配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lambda.tenant")
public class TenantProperties {

    /**
     * 域名识别租户相关配置
     */
    private TenantHost tenantHost = new TenantHost();

    @Getter
    @Setter
    public static class TenantHost {

        /**
         * 是否启用
         */
        private boolean enabled = false;
    }
}
