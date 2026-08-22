package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 对话执行的启动恢复编排：应用就绪时恢复或终结重启前遗留的中断态 Run、拉起待执行的 CREATED Run，并启动定时维护；
 * 具体动作委托给 {@link ChatRunCoordinator}。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRunRecoveryListener {

    private final ChatRunStateService runService;
    private final ChatRunCoordinator coordinator;
    private final AiProperties properties;

    /** 恢复服务启动前遗留的运行，并启动定时维护任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        for (ChatRunEntity run : runService.listInterruptedOnRestart(timedOutBefore())) {
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

    /** 心跳超时阈值：当前时间减去节点失效超时；早于该时刻未心跳（或无心跳）的中断态 Run 视为执行节点已失效。 */
    private LocalDateTime timedOutBefore() {
        return LocalDateTime.now().minusSeconds(properties.getChat().getRun().getInstanceLostTimeoutSeconds());
    }
}
