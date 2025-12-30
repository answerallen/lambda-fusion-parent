package com.lambda.fusion.datasource.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 远程数据源传输对象
 */
@Data
public class RemoteDataSource implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String datasourceName;
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    private String password; // 建议传输层加密
    private Integer enabled;
    private String tenantId; // 标识归属租户，null表示全局共享
    private long version;    // 数据版本号(时间戳)，用于同步校验
}
