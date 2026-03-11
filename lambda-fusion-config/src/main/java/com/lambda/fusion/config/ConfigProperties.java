package com.lambda.fusion.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@Setter
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
@ConfigurationProperties(prefix = "lambda.fusion.config")
public class ConfigProperties {

    private AutoRefresh autoRefresh = new AutoRefresh();

    private ConfigSql configSql = new ConfigSql();

    private DataSource dataSource = new DataSource();

    @Getter
    @Setter
    public static class AutoRefresh {

        private boolean enabled = true;

        /**
         * 刷新初始延迟时间，单位秒
         */
        private int initialDelaySeconds = 10;

        /**
         * 刷新间隔时间，单位秒
         */
        private int intervalSeconds = 30;

        /**
         * 刷新线程池核心线程数
         */
        private int corePoolSize = 1;
    }

    @Getter
    @Setter
    public static class DataSource {

        private int maxPoolSize = 1;

        private int minIdle = 1;

        private long connectionTimeout = 3000L;

        private String driverClassName;

        private String url;

        private String username;

        private String password;
    }

    @Getter
    @Setter
    public static class ConfigSql {

        private String selectConfigsSql =
                "SELECT property_key, property_value, application FROM la_configs WHERE application = ? OR application = 'public'";

        private String checkConfigsChangedSql =
                "SELECT MAX(update_time) FROM la_configs WHERE application = ? OR application = 'public'";
    }
}
