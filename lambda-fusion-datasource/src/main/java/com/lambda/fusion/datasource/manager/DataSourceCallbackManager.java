package com.lambda.fusion.datasource.manager;

import com.lambda.fusion.datasource.api.DataSourceChangeCallback;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
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

    @Data
    @AllArgsConstructor
    private static class SubscriberInfo {
        private String tenantId;
        private DataSourceChangeCallback callback;
    }

    private final Map<String, SubscriberInfo> subscribers = new ConcurrentHashMap<>();

    /**
     * 注册订阅
     *
     * @param clientId 客户端ID
     * @param tenantId 租户ID
     * @param callback 回调接口
     */
    public void addSubscriber(String clientId, String tenantId, DataSourceChangeCallback callback) {
        subscribers.put(clientId, new SubscriberInfo(tenantId, callback));
        log.info("Client subscribed: {} (Tenant: {})", clientId, tenantId);
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
        subscribers.forEach((clientId, info) -> {
            if (shouldNotify(info, dto)) {
                try {
                    info.getCallback().syncToLocal(dto);
                } catch (Exception e) {
                    log.warn("Failed to notify client {}: {}", clientId, e.getMessage());
                }
            }
        });
    }

    /**
     * 广播变更 - 移除本地
     *
     * @param dataSourceId 数据源ID
     */
    public void broadcastRemove(String dataSourceId) {
        // 移除操作无法判断租户，只能全广播
        // 本地移除通常只是移除内存中的池
        
        subscribers.forEach((clientId, info) -> {
            try {
                info.getCallback().removeLocal(dataSourceId);
            } catch (Exception e) {
                log.warn("Failed to notify client {}: {}", clientId, e.getMessage());
            }
        });
    }
    
    public void broadcastRemove(RemoteDataSource dto) {
        subscribers.forEach((clientId, info) -> {
            if (shouldNotify(info, dto)) {
                try {
                    info.getCallback().removeLocal(dto.getId());
                } catch (Exception e) {
                    log.warn("Failed to notify client {}: {}", clientId, e.getMessage());
                }
            }
        });
    }

    private boolean shouldNotify(SubscriberInfo info, RemoteDataSource dto) {
        // 全局数据源 -> 通知所有
        if (dto.getTenantId() == null) {
            return true;
        }
        // 订阅者是全局/管理员 -> 通知
        if (info.getTenantId() == null || "default".equals(info.getTenantId())) {
            return true;
        }
        // 租户匹配
        return info.getTenantId().equals(dto.getTenantId());
    }
}
