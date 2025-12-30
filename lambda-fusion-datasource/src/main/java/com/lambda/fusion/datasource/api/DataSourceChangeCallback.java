package com.lambda.fusion.datasource.api;

import com.lambda.fusion.datasource.model.RemoteDataSource;

/**
 * 数据源变更回调接口
 *
 * @author Jin
 */
public interface DataSourceChangeCallback {

    /**
     * 同步数据源到本地
     *
     * @param dataSourceDTO 数据源DTO
     */
    void syncToLocal(RemoteDataSource dataSourceDTO);

    /**
     * 移除本地数据源
     *
     * @param dataSourceId 数据源ID
     */
    void removeLocal(String dataSourceId);
}
