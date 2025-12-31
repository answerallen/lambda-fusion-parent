package com.lambda.fusion.datasource.event;

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
    public void handleDataSourceChange(DataSourceChangeEvent event) {
        executorService.submit(() -> {
            try {
                log.info(
                        "Received data source change event. ID: {}, Type: {}",
                        event.getDataSource().getId(),
                        event.isRemove() ? "REMOVE" : "UPDATE");

                if (event.isRemove()) {
                    // 使用重载方法支持租户过滤
                    callbackManager.broadcastRemove(event.getDataSource());
                } else {
                    callbackManager.broadcastSync(event.getDataSource());
                }
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
