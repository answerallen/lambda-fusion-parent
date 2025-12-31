package com.lambda.fusion.datasource.manager;

import com.lambda.fusion.datasource.api.DataSourceChangeCallback;
import com.lambda.fusion.datasource.api.DataSourceChangeEvent;
import com.lambda.fusion.datasource.model.SubscriberInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据源变更回调管理器
 * <p>
 * 管理所有订阅的Client回调，在数据源变更时广播通知。
 * </p>
 */
@Slf4j
@Component
public class DataSourceCallbackManager {

    /**
     * 已注册的回调映射 (clientId -> SubscriberInfo)
     */
    private final Map<String, SubscriberInfo> subscribers = new ConcurrentHashMap<>();

    /**
     * 异步通知线程池
     */
    private final ExecutorService notifyExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            Thread t = new Thread(r, "datasource-callback-notify");
            t.setDaemon(true);
            return t;
        }
    );

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
     * 广播变更事件到所有订阅者
     */
    public void broadcast(DataSourceChangeEvent event) {
        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for event: {}", event.getChangeType());
            return;
        }

        log.info("Broadcasting datasource change event: type={}, dataSourceId={}, subscribers={}",
            event.getChangeType(), event.getDataSourceId(), subscribers.size());

        subscribers.forEach((clientId, info) -> {
            if (shouldNotify(info, event)) {
                notifyExecutor.submit(() -> {
                    try {
                        info.getCallback().onDataSourceChanged(event);
                        log.debug("Notified client: {}", clientId);
                    } catch (Exception e) {
                        log.warn("Failed to notify client: {}, error: {}", clientId, e.getMessage());
                        // 通知失败时移除该客户端（可选策略，暂不移除以防网络抖动）
                    }
                });
            }
        });
    }

    private boolean shouldNotify(SubscriberInfo info, DataSourceChangeEvent event) {
        // 事件中的租户ID
        String eventTenantId = event.getTenantId();
        
        // 1. 如果事件是全局数据源变更 (tenantId == null)，通知所有有权限的订阅者
        if (eventTenantId == null) {
            // 这里假设所有租户都能看到全局数据源，或者根据具体业务规则判断
            return true;
        }

        // 2. 如果订阅者是全局/管理员 (info.tenantId == null or "default")，通知
        if (info.getTenantId() == null || "default".equals(info.getTenantId())) {
            return true;
        }

        // 3. 租户匹配
        return info.getTenantId().equals(eventTenantId);
    }
}
