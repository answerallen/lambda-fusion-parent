package com.lambda.fusion.config.datasource;

import static com.lambda.fusion.config.ConfigConstants.PUBLIC_APPLICATION;

import com.google.common.collect.Maps;
import com.lambda.fusion.config.ConfigProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public class DatabaseBasedProperties extends Properties {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public DatabaseBasedProperties(Connection connection, String application, ConfigProperties configProperties)
            throws SQLException {
        ConfigProperties.QueryConfig configSql = configProperties.getQueryConfig();
        Map<String, String> publicConfigs = Maps.newHashMap();
        Map<String, String> privateConfigs = Maps.newHashMap();
        try (PreparedStatement preparedStatement = connection.prepareStatement(configSql.getSelectConfigsSql())) {
            preparedStatement.setString(1, application);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String value = rs.getString(2);
                    String module = rs.getString(3);
                    if (StringUtils.isBlank(value)) {
                        continue;
                    }
                    if (PUBLIC_APPLICATION.equals(module)) {
                        privateConfigs.put(name, value);
                    } else {
                        publicConfigs.put(name, value);
                    }
                }
                privateConfigs.putAll(publicConfigs);
                privateConfigs.forEach(super::setProperty);
            }
        }
    }

    public DatabaseBasedProperties() {
        super();
    }

    public static String getCheckSum(Connection connection, String application, ConfigProperties configProperties)
            throws SQLException {
        ConfigProperties.QueryConfig configSql = configProperties.getQueryConfig();
        try (PreparedStatement preparedStatement = connection.prepareStatement(configSql.getCheckConfigsChangedSql())) {
            preparedStatement.setString(1, application);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return "";
    }

    public String[] getPropertyNames() {
        List<String> keys = this.keySet().stream().map(i -> (String) i).sorted().toList();
        return keys.toArray(new String[0]);
    }
}
