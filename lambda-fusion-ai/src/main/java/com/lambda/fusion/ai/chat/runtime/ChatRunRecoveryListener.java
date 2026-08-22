package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 对话执行的启动编排：应用就绪时拉起尚未开始的 CREATED Run，并启动本地定时维护。RUNNING 等业务状态不在
 * 启动时按节点归属恢复或终结；底层执行和跨调用状态由 AgentScope 负责。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRunRecoveryListener {

    private final ChatRunStateService runService;
    private final ChatRunCoordinator coordinator;

    /** 拉起尚未开始的运行，并启动定时维护任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        for (ChatRunEntity run : runService.listCreated()) {
            try {
                coordinator.startIfCreated(run);
            } catch (RuntimeException recoveryFailure) {
                log.error("启动待执行对话Run失败: runId={}", run.getId(), recoveryFailure);
            }
        }
        coordinator.scheduleMaintenance();
    }
}
