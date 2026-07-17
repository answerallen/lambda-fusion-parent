package com.lambda.fusion.datasource.event;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Component
@ConditionalOnBean(DataSourceChangeDispatcher.class)
@RequiredArgsConstructor
public class DataSourceListener {

    private final DataSourceChangeDispatcher dataSourceChangeDispatcher;
    private final DynamicDataSourceService dynamicDataSourceService;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            2,
            10,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);

                @Override
                public Thread newThread(@NonNull Runnable runnable) {
                    return new Thread(runnable, "lambda-fusion-ds-event-" + count.getAndIncrement());
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDataSourceChange(DataSourceEvent event) {
        executorService.submit(() -> {
            try {
                log.info(
                        "Received data source change event. ID: {}, Type: {}",
                        event.getDataSource().getId(),
                        event.getChangeType());

                DataSourceChangeEvent apiEvent = new DataSourceChangeEvent();
                apiEvent.setDataSourceId(event.getDataSource().getId());
                apiEvent.setDataSource(event.getDataSource());
                apiEvent.setTimestamp(System.currentTimeMillis());

                // 根据 DataSourceEvent.ChangeType 精确映射广播类型
                switch (event.getChangeType()) {
                    case ADD -> apiEvent.setChangeType(DatasourceConstants.ChangeType.ADD);
                    case UPDATE -> apiEvent.setChangeType(DatasourceConstants.ChangeType.UPDATE);
                    case DELETE -> {
                        apiEvent.setChangeType(DatasourceConstants.ChangeType.DELETE);
                        // 在事务提交后（AFTER_COMMIT）移除本地连接池，避免在 Service 中出现副作用（无法回滚）
                        dynamicDataSourceService.removeDataSource(
                                event.getDataSource().getId());
                    }
                    default -> {
                        log.warn("Unknown DataSourceEvent.ChangeType: {}", event.getChangeType());
                        return;
                    }
                }

                dataSourceChangeDispatcher.broadcast(apiEvent);
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
