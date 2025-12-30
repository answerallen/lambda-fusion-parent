package com.lambda.fusion.datasource.event;

import com.lambda.fusion.datasource.manager.DataSourceCallbackManager;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDataSourceChange(DataSourceChangeEvent event) {
        log.info("Received data source change event. ID: {}, Type: {}",
                event.getDataSource().getId(),
                event.isRemove() ? "REMOVE" : "UPDATE");

        if (event.isRemove()) {
            callbackManager.broadcastRemove(event.getDataSource().getId());
        } else {
            callbackManager.broadcastSync(event.getDataSource());
        }
    }
}
