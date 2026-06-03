package com.lambda.fusion.datasource.dispatcher;

import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 数据源变更事件
 */
@Data
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DataSourceChangeEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更类型
     */
    private DatasourceConstants.ChangeType changeType;

    /**
     * 数据源ID
     */
    private String dataSourceId;

    /**
     * 租户ID（全局事件可为空）
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
}
