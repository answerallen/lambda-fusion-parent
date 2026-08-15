package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 对话执行的启动恢复编排。
 *
 * <p>应用就绪时恢复或终结重启前遗留的中断态 Run、拉起待执行的 CREATED Run，并启动定时维护任务；
 * 具体恢复动作委托给 {@link ChatRunCoordinator}。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRunStartupRecovery {

    private final ChatRunStateService runService;
    private final ChatRunCoordinator coordinator;

    /** 恢复服务启动前遗留的运行，并启动定时维护任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        for (ChatRunEntity run : runService.listInterruptedOnRestart()) {
            try {
                coordinator.recoverInterrupted(run);
            } catch (RuntimeException recoveryFailure) {
                log.error("恢复遗留对话Run失败: runId={}", run.getId(), recoveryFailure);
            }
        }
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
