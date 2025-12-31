package com.lambda.fusion.datasource.event;

import com.lambda.fusion.datasource.api.DataSourceChangeEvent;
import com.lambda.fusion.datasource.manager.DataSourceCallbackManager;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 数据源变更事件监听器
 * 负责在事务提交后广播变更通知
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceEventListener {

    private final DataSourceCallbackManager callbackManager;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            2,
            10,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "ds-event-" + count.getAndIncrement());
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDataSourceChange(LocalDataSourceChangeEvent event) {
        executorService.submit(() -> {
            try {
                log.info(
                        "Received data source change event. ID: {}, Type: {}",
                        event.getDataSource().getId(),
                        event.isRemove() ? "REMOVE" : "UPDATE");

                DataSourceChangeEvent apiEvent =
                        new DataSourceChangeEvent();
                
                if (event.isRemove()) {
                    apiEvent.setChangeType(DataSourceChangeEvent.ChangeType.DELETE);
                } else {
                    // 默认为 UPDATE，如果是新增场景在 Service 层应复用此事件或区分
                    // 此处为了兼容，假设非移除即为更新/新增，具体由 Client 端幂等处理
                    apiEvent.setChangeType(DataSourceChangeEvent.ChangeType.UPDATE);
                }
                
                apiEvent.setDataSourceId(event.getDataSource().getId());
                apiEvent.setTenantId(event.getDataSource().getTenantId());
                apiEvent.setDataSource(event.getDataSource());
                apiEvent.setTimestamp(System.currentTimeMillis());

                callbackManager.broadcast(apiEvent);
            } catch (Exception e) {
                log.error("Failed to handle data source change event", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
