package com.lambda.fusion.autoconfig;

import cn.hutool.core.collection.CollUtil;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@ConfigurationProperties(prefix = "lambda.authorize")

public class AuthorizeProperties {
    /**
     * 是否开启用户注册功能
     */
    private boolean enabledRegistered;
    /**
     * 是否开启数据角色
     */
    private boolean dataRoleEnabled = false;

    /**
     * 组织名称作为id使用并入库
     */
    private boolean organizationNameAsId = false;
    /**
     * 新平台配置相关
     */
    private NewPlatform newPlatform = new NewPlatform();
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

    @Data
    
    public static class NewPlatform {
        /**
         * 是否开启新平台配置
         */
        private boolean enabled = false;
        /**
         * 所属新平台的系统编号
         */
        @NotNull
        private String systemId;
        /***
         * 是否同步数据
         */
        private boolean synchronize = true;
        /**
         * 新平台的接口地址前缀
         */
        private String url = "http://127.0.0.1:8080";

        /**
         * 是否开启基础平台单点登陆
         */
        private boolean basis = false;
    }


    @Getter
    @Setter
    
    public static class DevRole {
        private final String[] defaultWhiteArray = new String[]{
                "/public/**",
                "/deploy/**",
                "/upload/**",
                "**/dictionaries/**",
                "/dictionaries/**",
                "**/purviews/**",
                "/purviews/**",
                "/workflow/**",
                "/report/**",
                "/dataease/**",
                "/authority/**",
                "/fence/**",
                "/onlform/**",
                "/api-token/**",
                "/calendar/**",
                "/report/**",
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
