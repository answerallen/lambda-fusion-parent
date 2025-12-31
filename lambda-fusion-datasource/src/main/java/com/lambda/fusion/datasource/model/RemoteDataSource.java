package com.lambda.fusion.datasource.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 远程数据源传输对象
 */
@AutoConverter(target = UpsertDataSource.class)
@Getter
@Setter
public class RemoteDataSource implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String datasourceName;
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    private String password;
    private Integer enabled;
    private String tenantId; // 标识归属租户，null表示全局共享
    private String dbType; // 数据库类型
    private long version; // 数据版本号(时间戳)，用于同步校验
}
