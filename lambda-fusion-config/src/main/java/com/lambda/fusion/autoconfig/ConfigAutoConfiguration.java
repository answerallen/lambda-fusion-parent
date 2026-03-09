package com.lambda.fusion.autoconfig;

import com.lambda.fusion.config.ConfigConfigure;
import com.lambda.fusion.config.datasource.DatabaseBasedPropertySourceLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@AutoConfiguration
@Import(ConfigConfigure.class)
public class ConfigAutoConfiguration {

    @Bean
    public DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator() {
        return new DatabaseBasedPropertySourceLocator();
    }

}
