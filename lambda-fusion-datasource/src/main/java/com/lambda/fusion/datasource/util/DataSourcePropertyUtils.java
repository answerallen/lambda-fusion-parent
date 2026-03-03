package com.lambda.fusion.datasource.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.dialect.DriverUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.DynamicDataSourceProperty;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import liquibase.util.JdbcUtil;

import static com.baomidou.mybatisplus.annotation.DbType.*;

public class DataSourcePropertyUtils {

    public static DataSourceProperty getDataSourceProperty(DataSourceEntity entity) {
        DynamicDataSourceProperty property = new DynamicDataSourceProperty();
        property.setPoolName(entity.getDatasourceName());
        property.setId(entity.getId());
        property.setUrl(buildJdbcUrl(
                entity.getDbType(), entity.getHost(), entity.getPort(), entity.getDbName(), entity.getExtraConfig()));
        property.setUsername(entity.getUsername());
        property.setPassword(entity.getPassword());
        property.setDriverClassName(resolveDriverClassName(entity.getDbType(), entity.getExtraConfig()));
        return property;
    }

    public static DataSourceProperty getDataSourceProperty(RemoteDataSource remoteDataSource) {
        DynamicDataSourceProperty property = new DynamicDataSourceProperty();
        property.setPoolName(remoteDataSource.getDatasourceName());
        property.setId(remoteDataSource.getId());
        property.setUrl(
                buildJdbcUrl(remoteDataSource.getDbType(), remoteDataSource.getHost(), remoteDataSource.getPort(), remoteDataSource.getDbName(), remoteDataSource.getExtraConfig()));
        property.setUsername(remoteDataSource.getUsername());
        if (remoteDataSource.getPassword() != null) {
            property.setPassword(Base64.decodeStr(remoteDataSource.getPassword()));
        }
        property.setDriverClassName(resolveDriverClassName(remoteDataSource.getDbType(), remoteDataSource.getExtraConfig()));
        return property;
    }

    private static String resolveDriverClassName(String dbType, String extraConfig) {
        String fromConfig = readJsonString(extraConfig, "driverClassName");
        if (StrUtil.isNotBlank(fromConfig)) {
            return fromConfig;
        }
        return DriverUtil.identifyDriver(dbType);
    }

    private static String buildJdbcUrl(String dbType, String host, Integer port, String dbName, String extraConfig) {
        String fromConfig = readJsonString(extraConfig, "jdbcUrl");
        if (StrUtil.isNotBlank(fromConfig)) {
            return fromConfig;
        }
        if (StrUtil.isBlank(host) || port == null || StrUtil.isBlank(dbName)) {
            return null;
        }
        String normalized = normalizeDbType(dbType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + dbName;
            case "pg", "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
            case "oracle" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + dbName;
            default -> null;
        };
    }

    private static String normalizeDbType(String dbType) {
        if (StrUtil.isBlank(dbType)) {
            return null;
        }
        return dbType.trim().toLowerCase();
    }

    private static String readJsonString(String json, String field) {
        if (StrUtil.isBlank(json) || StrUtil.isBlank(field)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json).getStr(field);
        } catch (Exception ignored) {
            return null;
        }
    }
}
