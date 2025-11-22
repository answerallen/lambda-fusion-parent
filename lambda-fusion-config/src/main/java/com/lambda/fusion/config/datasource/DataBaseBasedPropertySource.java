package com.lambda.fusion.config.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import javax.annotation.Nonnull;
import org.springframework.core.env.EnumerablePropertySource;

public class DataBaseBasedPropertySource extends EnumerablePropertySource<DatabaseBasedProperties> {

    public DataBaseBasedPropertySource(String name, Connection connection, String application) throws SQLException {
        super(name, new DatabaseBasedProperties(connection, application));
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
