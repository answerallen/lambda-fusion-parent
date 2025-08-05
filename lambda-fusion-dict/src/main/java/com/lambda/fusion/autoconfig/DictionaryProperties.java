package com.lambda.fusion.autoconfig;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DictionaryProperties
 *
 * @author jin
 */
@Getter
@Setter
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "lambda.fusion.dictionary")
public class DictionaryProperties {

    /**
     * 是否允许级联删除字典
     */
    private boolean allowedCascadeDelete;

    /**
     * http 字典远程地址前缀  如：http://127.0.0.1:20003
     */
    private String httpRemoteHostPrefix;

    /**
     * 是否启用字典Dubbo服务
     */
    private Boolean enableDubboProvider = false;
}
