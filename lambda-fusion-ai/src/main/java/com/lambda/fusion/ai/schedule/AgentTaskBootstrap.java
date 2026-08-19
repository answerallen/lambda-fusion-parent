package com.lambda.fusion.ai.schedule;

import com.lambda.fusion.ai.AiConstants.SubAgentCategory;
import com.lambda.fusion.ai.subagent.mapper.SubAgentMapper;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 定时任务启动装载：应用启动后扫描 {@code ai_sub_agent} 中
 * {@code category=SCHEDULED_TASK AND schedule_enabled=1} 的记录并提交调度。
 *
 * <p>扩展把 Agent 定义存内存（重启后 Quartz 仅剩触发器，任务成控制壳），故以 DB 为唯一事实来源
 * 在启动时重建（工程契约 §20.3）。扫描需跨租户遍历，属明确的启动恢复职责，经
 * {@code TenantContextHolder} 系统级缺省策略执行（契约 §5.1）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AgentTaskBootstrap implements ApplicationRunner {

    private final SubAgentMapper subAgentMapper;
    private final AgentTaskScheduler agentTaskScheduler;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        List<SubAgentEntity> tasks =
                subAgentMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SubAgentEntity>lambdaQuery()
                        .eq(SubAgentEntity::getCategory, SubAgentCategory.SCHEDULED_TASK.getCode())
                        .eq(SubAgentEntity::getScheduleEnabled, Boolean.TRUE));
        int ok = 0;
        for (SubAgentEntity entity : tasks) {
            try {
                agentTaskScheduler.scheduleTask(entity);
                ok++;
            } catch (Exception e) {
                // 单个任务装载失败不阻塞其余与启动
                log.error("定时任务启动装载失败: id={}, name={}", entity.getId(), entity.getName(), e);
            }
        }
        log.info("定时任务启动装载完成: 共 {} 条, 成功 {} 条", tasks.size(), ok);
    }
}
