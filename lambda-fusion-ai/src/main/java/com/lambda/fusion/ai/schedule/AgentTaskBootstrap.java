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
 * 应用启动后加载所有已启用的定时任务，并重新提交到运行时调度器。
 *
 * <p>Agent 定义只保存在内存中，重启后必须以 {@code ai_sub_agent} 的持久化配置重建。
 * 启动恢复需要覆盖全部租户，因此该查询有意使用系统级租户策略执行。
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
                // 隔离单个任务的配置或装配错误，避免阻塞其他任务及应用启动。
                log.error("定时任务启动装载失败: id={}, name={}", entity.getId(), entity.getName(), e);
            }
        }
        log.info("定时任务启动装载完成: 共 {} 条, 成功 {} 条", tasks.size(), ok);
    }
}
