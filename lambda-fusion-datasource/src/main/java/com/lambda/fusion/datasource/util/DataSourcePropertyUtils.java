package com.lambda.fusion.datasource.util;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.model.DataSourceEntity;

public class DataSourcePropertyUtils {

    public static DataSourceProperty getDataSourceProperty(DataSourceEntity entity) {
        DataSourceProperty property = new DataSourceProperty();
        property.setId(entity.getId());
        property.setUrl(entity.getJdbcUrl());
        property.setUsername(entity.getUsername());
        property.setPassword(entity.getPassword());
        property.setDriverClassName(entity.getDriverClassName());
        return property;
    }

}
