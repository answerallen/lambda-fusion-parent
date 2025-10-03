package com.lambda.fusion.authority;

import cn.hutool.core.collection.CollUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lambda.fusion.authorize")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class AuthorityProperties {
    /**
     * 是否开启数据角色
     */
    private boolean dataRoleEnabled = false;

    /**
     * 组织名称作为id使用并入库
     */
    private boolean organizationNameAsId = false;

    /**
     * 密码策略
     */
    private PasswordStrategy passwordStrategy = new PasswordStrategy();
    /**
     * 开发者角色配置
     */
    private DevRole devRole = new DevRole();

    /**
     * 密码策略
     */
    @Data
    public static class PasswordStrategy {
        /**
         * 密码策略模式
         */
        Mode mode = Mode.RANDOM;
        /**
         * 当密码策略为固定值时生效
         */
        String customize = "123456";

        /**
         * 启用密码有效期
         */
        private Boolean enablePeriodChange = false;

        /**
         * 密码有效期天数 默认 90 天
         */
        private Integer periodChangeDays = 90;

        /**
         * 密码策略模式
         */
        public enum Mode {
            /**
             * 固定值
             */
            FIXED,
            /**
             * 随机值
             */
            RANDOM,
            /**
             * 加密值
             */
            CIPHERTEXT
        }
    }

    @Getter
    @Setter
    public static class DevRole {
        private final String[] defaultWhiteArray = new String[] {
            "/public/**",
            "**/dictionaries/**",
            "/dictionaries/**",
            "/authority/**",
            "/monitor/**",
            "/error/**",
            "/error",
            "/v3/**",
        };
        private List<String> whiteList;

        public List<String> getWhiteList() {
            List<String> list = new ArrayList<>();
            CollUtil.addAll(list, whiteList);
            CollUtil.addAll(list, defaultWhiteArray);
            return list;
        }
    }
}
