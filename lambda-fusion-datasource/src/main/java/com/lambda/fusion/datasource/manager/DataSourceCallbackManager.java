package com.lambda.fusion.datasource.manager;

import com.lambda.fusion.datasource.api.DataSourceChangeCallback;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源回调管理器
 * 管理客户端订阅并广播变更
 *
 * @author Jin
 */
@Slf4j
@Component
public class DataSourceCallbackManager {

    private final Map<String, DataSourceChangeCallback> subscribers = new ConcurrentHashMap<>();

    /**
     * 注册订阅
     *
     * @param clientId 客户端ID
     * @param callback 回调接口
     */
    public void addSubscriber(String clientId, DataSourceChangeCallback callback) {
        subscribers.put(clientId, callback);
        log.info("Client subscribed: {}", clientId);
    }

    /**
     * 移除订阅
     *
     * @param clientId 客户端ID
     */
    public void removeSubscriber(String clientId) {
        subscribers.remove(clientId);
        log.info("Client unsubscribed: {}", clientId);
    }

    /**
     * 广播变更 - 同步到本地
     *
     * @param dto 数据源DTO
     */
    public void broadcastSync(RemoteDataSource dto) {
        subscribers.forEach((clientId, callback) -> {
            try {
                callback.syncToLocal(dto);
            } catch (Exception e) {
                log.warn("Failed to notify client {}: {}", clientId, e.getMessage());
                // 可以在这里做一些容错处理，比如移除失效的客户端
            }
        });
    }

    /**
     * 广播变更 - 移除本地
     *
     * @param dataSourceId 数据源ID
     */
    public void broadcastRemove(String dataSourceId) {
        subscribers.forEach((clientId, callback) -> {
            try {
                callback.removeLocal(dataSourceId);
            } catch (Exception e) {
                log.warn("Failed to notify client {}: {}", clientId, e.getMessage());
            }
        });
    }
}
