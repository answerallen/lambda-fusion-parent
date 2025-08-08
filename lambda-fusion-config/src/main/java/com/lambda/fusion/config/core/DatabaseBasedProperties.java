package com.lambda.fusion.config.core;

import static com.lambda.fusion.config.ConfigConstants.Database.*;

import com.google.common.collect.Maps;
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

    private static final String SQL = SELECT_CONFIGS_SQL;

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public DatabaseBasedProperties(Connection connection, String application) throws SQLException {
        Map<String, String> privated = Maps.newHashMap();
        Map<String, String> publiced = Maps.newHashMap();
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL)) {
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
                        publiced.put(name, value);
                    } else {
                        privated.put(name, value);
                    }
                    publiced.putAll(privated);
                    publiced.forEach(this::setProperty);
                }
            }
        }
    }

    public DatabaseBasedProperties() {
        super();
    }

    public String[] getPropertyNames() {
        List<String> keys = this.keySet().stream().map(i -> (String) i).sorted().toList();
        return keys.toArray(new String[0]);
    }
}
