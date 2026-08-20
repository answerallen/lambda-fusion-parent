package com.lambda.fusion.ai.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.ai.AiConstants.SubAgentCategory;
import com.lambda.fusion.ai.AiConstants.TaskTriggerType;
import com.lambda.fusion.ai.schedule.service.ScheduledTaskLogService;
import com.lambda.fusion.ai.subagent.mapper.SubAgentMapper;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

/**
 * 定时任务执行监听器：挂在共享 Quartz {@code Scheduler} 上，覆盖「定时触发」路径
 * （该路径不经过 {@code AgentTaskScheduler.TenantAwareTask}）。
 *
 * <p>职责二合一：
 * <ul>
 *   <li>{@link #jobToBeExecuted}：按 JobDataMap 的 taskName({@code tenantId:name}）解析租户并恢复
 *   {@link TenantContextHolder}，补齐定时路径缺失的 DB 层租户上下文（契约 §5.1 后台任务例外）。</li>
 *   <li>{@link #jobWasExecuted}：按 {@code jobException} 判成败、{@code getJobRunTime()} 取耗时，
 *   落一条 SCHEDULED 执行记录；清理租户上下文。</li>
 * </ul>
 *
 * <p>注意：Quartz Job 不回传 Agent 返回的 {@code Msg}，定时路径 output 暂记空（见设计文档开放点）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AgentExecutionJobListener implements JobListener {

    private final ScheduledTaskLogService scheduledTaskLogService;
    private final SubAgentMapper subAgentMapper;

    @Override
    public String getName() {
        return "agentExecutionJobListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        String tenantId = parseTenantId(context);
        if (StringUtils.isNotBlank(tenantId)) {
            TenantContextHolder.getInstance().setTenantId(tenantId);
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // 触发被否决（如暂停态），不记录
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        try {
            String tenantId = parseTenantId(context);
            String taskName = parseTaskName(context);
            if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(taskName)) {
                return;
            }
            boolean success = jobException == null;
            String errorMessage = success ? null : String.valueOf(jobException.getMessage());
            LocalDateTime finishedAt = LocalDateTime.now();
            // getFireTime 为本次触发时间，作为开始时间；getJobRunTime 为执行耗时
            LocalDateTime startedAt = context.getFireTime() == null
                    ? finishedAt
                    : LocalDateTime.ofInstant(context.getFireTime().toInstant(), ZoneId.systemDefault());
            String taskId = resolveTaskId(tenantId, taskName);
            scheduledTaskLogService.record(
                    tenantId,
                    taskId,
                    taskName,
                    TaskTriggerType.SCHEDULED.name(),
                    success,
                    null,
                    errorMessage,
                    startedAt,
                    finishedAt);
        } catch (Exception e) {
            log.error("定时任务执行记录写入失败: jobKey={}", context.getJobDetail().getKey(), e);
        } finally {
            TenantContextHolder.getInstance().close();
        }
    }

    /** 从 JobDataMap 的 taskName({@code tenantId:name}）解析租户。 */
    private String parseTenantId(JobExecutionContext context) {
        String taskName = context.getJobDetail().getJobDataMap().getString("taskName");
        if (StringUtils.isBlank(taskName)) {
            return null;
        }
        int idx = taskName.indexOf(':');
        return idx > 0 ? taskName.substring(0, idx) : null;
    }

    /** 从 JobDataMap 的 taskName({@code tenantId:name}）解析任务名。 */
    private String parseTaskName(JobExecutionContext context) {
        String taskName = context.getJobDetail().getJobDataMap().getString("taskName");
        if (StringUtils.isBlank(taskName)) {
            return null;
        }
        int idx = taskName.indexOf(':');
        return idx > 0 ? taskName.substring(idx + 1) : taskName;
    }

    /** 按租户 + 任务名解析业务任务ID（ai_sub_agent.id）；查不到时退化用任务名占位。 */
    private String resolveTaskId(String tenantId, String taskName) {
        try {
            SubAgentEntity entity = subAgentMapper.selectOne(new LambdaQueryWrapper<SubAgentEntity>()
                    .eq(SubAgentEntity::getTenantId, tenantId)
                    .eq(SubAgentEntity::getCategory, SubAgentCategory.SCHEDULED_TASK.getCode())
                    .eq(SubAgentEntity::getName, taskName));
            return entity == null ? taskName : entity.getId();
        } catch (Exception e) {
            log.warn("定时任务ID解析失败,退化用任务名: tenant={}, name={}", tenantId, taskName);
            return taskName;
        }
    }
}
