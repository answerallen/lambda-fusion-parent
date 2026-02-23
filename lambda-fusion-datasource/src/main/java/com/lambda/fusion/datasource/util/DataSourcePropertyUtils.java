package com.lambda.fusion.datasource.util;

import cn.hutool.core.codec.Base64;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.DynamicDataSourceProperty;
import com.lambda.fusion.datasource.model.RemoteDataSource;

public class DataSourcePropertyUtils {

    public static DataSourceProperty getDataSourceProperty(DataSourceEntity entity) {
        DynamicDataSourceProperty property = new DynamicDataSourceProperty();
        property.setPoolName(entity.getDatasourceName());
        property.setId(entity.getId());
        property.setUrl(entity.getJdbcUrl());
        property.setUsername(entity.getUsername());
        property.setPassword(entity.getPassword());
        property.setDriverClassName(entity.getDriverClassName());
        return property;
    }

    public static DataSourceProperty getDataSourceProperty(RemoteDataSource dto) {
        DynamicDataSourceProperty property = new DynamicDataSourceProperty();
        property.setPoolName(dto.getDatasourceName());
        property.setId(dto.getId());
        property.setUrl(dto.getJdbcUrl());
        property.setUsername(dto.getUsername());
        if (dto.getPassword() != null) {
            // P2-10 修复：反解 Base64 的密码，建立物理连接需要明文
            property.setPassword(Base64.decodeStr(dto.getPassword()));
        }
        property.setDriverClassName(dto.getDriverClassName());
        return property;
    }
}
