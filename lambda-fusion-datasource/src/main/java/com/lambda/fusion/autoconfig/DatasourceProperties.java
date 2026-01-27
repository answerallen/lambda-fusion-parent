package com.lambda.fusion.autoconfig;

import com.lambda.fusion.datasource.DatasourceConstant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源模块配置属性
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@Data
@ConfigurationProperties(prefix = DatasourceConstant.PREFIX)
public class DatasourceProperties {

    /**
     * 运行模式
     * <p>
     * server: 服务端模式，读取本地数据库，对外提供数据源信息
     * client: 客户端模式，远程订阅数据源信息
     */
    private String mode = DatasourceConstant.DEFAULT_MODE;

    /**
     * Dubbo 相关配置
     */
    private Dubbo dubbo = new Dubbo();

    @Data
    public static class Dubbo {
        /**
         * 服务分组
         */
        private String group = DatasourceConstant.DUBBO_GROUP;

        /**
         * 服务版本
         */
        private String version = DatasourceConstant.DUBBO_VERSION;
    }
}
