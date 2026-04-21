package com.lambda.fusion.datasource.commons.dispatcher;

import com.lambda.fusion.datasource.commons.api.DataSourceChangeEvent;
import com.lambda.fusion.datasource.commons.api.DataSourceChangeListener;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源变更回调管理器
 * <p>
 * 管理所有订阅的Client回调，在数据源变更时广播通知。
 * </p>
 */
@Slf4j
public class DataSourceChangeDispatcher {

    /**
     * 已注册的回调映射 (clientId -> callback)
     */
    private final Map<String, DataSourceChangeListener> subscribers = new ConcurrentHashMap<>();

    /**
     * 异步通知线程池
     * 使用有界队列防止OOM，使用CallerRunsPolicy进行背压
     */
    private final ExecutorService notifyExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "datasource-callback-notify");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @PreDestroy
    public void shutdown() {
        notifyExecutor.shutdown();
        try {
            if (!notifyExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                notifyExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            notifyExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 注册订阅
     *
     * @param clientId       客户端ID
     * @param changeListener 回调接口
     */
    public void addSubscriber(String clientId, DataSourceChangeListener changeListener) {
        subscribers.put(clientId, changeListener);
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
     * 广播变更事件到所有订阅者
     */
    public void broadcast(DataSourceChangeEvent event) {
        if (subscribers.isEmpty()) {
            log.debug("No subscribers to notify for event: {}", event.getChangeType());
            return;
        }

        log.info(
                "Broadcasting datasource change event: type={}, dataSourceId={}, subscribers={}",
                event.getChangeType(),
                event.getDataSourceId(),
                subscribers.size());

        subscribers.forEach((clientId, listener) -> notifyExecutor.submit(() -> {
            try {
                listener.onDataSourceChanged(event);
                log.debug("Notified client: {}", clientId);
            } catch (Exception e) {
                log.warn("Failed to notify client: {}", clientId, e);
            }
        }));
    }
}
