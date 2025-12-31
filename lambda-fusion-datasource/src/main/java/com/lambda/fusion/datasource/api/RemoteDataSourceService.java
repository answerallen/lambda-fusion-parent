package com.lambda.fusion.datasource.api;

import com.lambda.fusion.datasource.model.RemoteDataSource;
import java.util.List;

/**
 * 远程数据源管理服务接口
 * <p>
 * Dubbo RPC服务定义，支持回调通知机制。
 * </p>
 */
public interface RemoteDataSourceService {

    /**
     * 查询所有数据源
     */
    List<RemoteDataSource> listAll();

    /**
     * 查询所有启用的数据源
     */
    List<RemoteDataSource> listEnabled();

    /**
     * 根据ID查询数据源
     */
    RemoteDataSource get(String id);

    /**
     * 新增数据源
     */
    boolean add(RemoteDataSource dto);

    /**
     * 更新数据源
     */
    boolean update(String id, RemoteDataSource dto);

    /**
     * 删除数据源
     */
    boolean delete(String id);

    /**
     * 测试数据源连接
     */
    boolean test(String id);

    /**
     * 启用数据源
     */
    boolean enable(String id);

    /**
     * 禁用数据源
     */
    boolean disable(String id);

    /**
     * 订阅数据源变更通知
     * <p>
     * Client调用此方法注册回调，Server在数据源变更时调用回调通知Client。
     * </p>
     *
     * @param clientId 客户端唯一标识（建议使用 应用名+IP+端口）
     * @param callback 回调接口实现
     */
    void subscribe(String clientId, DataSourceChangeListener callback);

    /**
     * 取消订阅数据源变更通知
     *
     * @param clientId 客户端唯一标识
     */
    void unsubscribe(String clientId);
}
