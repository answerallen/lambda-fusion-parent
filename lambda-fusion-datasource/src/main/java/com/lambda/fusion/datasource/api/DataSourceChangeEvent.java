package com.lambda.fusion.datasource.api;

import com.lambda.fusion.datasource.model.RemoteDataSource;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 数据源变更事件
 */
@Data
public class DataSourceChangeEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更类型
     */
    private ChangeType changeType;

    /**
     * 数据源ID
     */
    private String dataSourceId;

    /**
     * 租户ID（可选，用于租户级数据源）
     */
    private String tenantId;

    /**
     * 变更后的数据源信息（DELETE时为null）
     */
    private RemoteDataSource dataSource;

    /**
     * 变更时间戳
     */
    private long timestamp;

    public enum ChangeType {
        /** 新增 */
        ADD,
        /** 更新 */
        UPDATE,
        /** 删除 */
        DELETE,
        /** 启用 */
        ENABLE,
        /** 禁用 */
        DISABLE
    }
}
