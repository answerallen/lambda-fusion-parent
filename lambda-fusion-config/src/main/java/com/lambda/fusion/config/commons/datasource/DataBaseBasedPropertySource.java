package com.lambda.fusion.config.commons.datasource;

import com.lambda.fusion.config.ConfigProperties;
import java.sql.Connection;
import java.sql.SQLException;
import javax.annotation.Nonnull;
import org.springframework.core.env.EnumerablePropertySource;

public class DataBaseBasedPropertySource extends EnumerablePropertySource<DatabaseBasedProperties> {

    public DataBaseBasedPropertySource(
            String name, Connection connection, String application, ConfigProperties configProperties)
            throws SQLException {
        super(name, new DatabaseBasedProperties(connection, application, configProperties));
    }

    public DataBaseBasedPropertySource(String name, DatabaseBasedProperties properties) {
        super(name, properties);
    }

    @Override
    public Object getProperty(@Nonnull String name) {
        return getSource().get(name);
    }

    @Nonnull
    @Override
    public String[] getPropertyNames() {
        return getSource().getPropertyNames();
    }
}
