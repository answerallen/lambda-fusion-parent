package com.lambda.fusion.datasource.api.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 远程数据源 DTO
 */
@Data
public class RemoteDataSourceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String datasourceName;
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    private String password;
    private Integer enabled;
    private String tenantId;
    private String dbType; // Added for tenant datasource requirement
}
