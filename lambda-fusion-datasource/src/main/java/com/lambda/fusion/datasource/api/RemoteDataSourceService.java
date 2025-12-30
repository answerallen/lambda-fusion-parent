package com.lambda.fusion.datasource.api;

import com.lambda.fusion.datasource.model.RemoteDataSource;

import java.util.List;
import java.util.Map;

/**
 * 远程数据源服务接口
 *
 * @author Jin
 */
public interface RemoteDataSourceService {
    /**
     * 获取当前上下文可见的所有已启用数据源
     * (包含：全局启用数据源 + 当前租户启用数据源)
     *
     * @return 数据源列表
     */
    List<RemoteDataSource> listActiveDataSources();

    /**
     * 订阅变更 (Client -> Server)
     *
     * @param clientId 客户端ID
     * @param callback 回调接口
     */
    void subscribe(String clientId, DataSourceChangeCallback callback);

    /**
     * 取消订阅
     *
     * @param clientId 客户端ID
     */
    void unsubscribe(String clientId);

    /**
     * 获取当前数据源版本指纹 (用于一致性校验)
     * 返回 Map<DataSourceId, Version>
     *
     * @return 版本映射
     */
    Map<String, Long> getDataSourcesVersion();
}
