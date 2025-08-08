package com.lambda.fusion.config.core;

import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

public class DatabaseBasedEnvironment extends StandardEnvironment {

    public DatabaseBasedEnvironment(MutablePropertySources propertySources) {
        super(propertySources);
    }
}
