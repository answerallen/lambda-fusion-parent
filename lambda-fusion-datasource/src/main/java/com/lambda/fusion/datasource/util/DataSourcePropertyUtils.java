package com.lambda.fusion.datasource.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.DynamicDataSourceProperty;
import com.lambda.fusion.datasource.model.RemoteDataSource;

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

    public static DataSourceProperty getDataSourceProperty(RemoteDataSource dto) {
        DynamicDataSourceProperty property = new DynamicDataSourceProperty();
        property.setPoolName(dto.getDatasourceName());
        property.setId(dto.getId());
        property.setUrl(
                buildJdbcUrl(dto.getDbType(), dto.getHost(), dto.getPort(), dto.getDbName(), dto.getExtraConfig()));
        property.setUsername(dto.getUsername());
        if (dto.getPassword() != null) {
            property.setPassword(Base64.decodeStr(dto.getPassword()));
        }
        property.setDriverClassName(resolveDriverClassName(dto.getDbType(), dto.getExtraConfig()));
        return property;
    }

    public static RemoteDataSource buildDataSourceEntity(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = new RemoteDataSource();
        remoteDataSource.setId(entity.getId());
        remoteDataSource.setDatasourceKey(entity.getDatasourceKey());
        remoteDataSource.setDatasourceName(entity.getDatasourceName());
        remoteDataSource.setDbType(entity.getDbType());
        remoteDataSource.setPurpose(entity.getPurpose());
        remoteDataSource.setHost(entity.getHost());
        remoteDataSource.setPort(entity.getPort());
        remoteDataSource.setDbName(entity.getDbName());
        remoteDataSource.setUsername(entity.getUsername());
        if (entity.getPassword() != null) {
            remoteDataSource.setPassword(Base64.encode(entity.getPassword()));
        }
        remoteDataSource.setNodeRole(entity.getNodeRole());
        remoteDataSource.setStatus(entity.getStatus());
        remoteDataSource.setTags(entity.getTags());
        remoteDataSource.setExtraConfig(entity.getExtraConfig());
        return remoteDataSource;
    }

    private static String resolveDriverClassName(String dbType, String extraConfig) {
        String fromConfig = readJsonString(extraConfig, "driverClassName");
        if (StrUtil.isNotBlank(fromConfig)) {
            return fromConfig;
        }
        String normalized = normalizeDbType(dbType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "pg", "postgresql" -> "org.postgresql.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> null;
        };
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
