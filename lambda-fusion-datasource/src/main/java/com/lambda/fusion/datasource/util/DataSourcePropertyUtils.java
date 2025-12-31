package com.lambda.fusion.datasource.util;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.api.dto.RemoteDataSourceDTO;
import com.lambda.fusion.datasource.model.DataSourceEntity;

public class DataSourcePropertyUtils {

    public static DataSourceProperty getDataSourceProperty(DataSourceEntity entity) {
        DataSourceProperty property = new DataSourceProperty();
        property.setId(entity.getId()); // ID for internal reference
        property.setUrl(entity.getJdbcUrl());
        property.setUsername(entity.getUsername());
        property.setPassword(entity.getPassword());
        property.setDriverClassName(entity.getDriverClassName());
        return property;
    }

    public static DataSourceProperty getDataSourceProperty(RemoteDataSourceDTO dto) {
        DataSourceProperty property = new DataSourceProperty();
        property.setId(dto.getId());
        property.setUrl(dto.getJdbcUrl());
        property.setUsername(dto.getUsername());
        property.setPassword(dto.getPassword());
        property.setDriverClassName(dto.getDriverClassName());
        return property;
    }
}
