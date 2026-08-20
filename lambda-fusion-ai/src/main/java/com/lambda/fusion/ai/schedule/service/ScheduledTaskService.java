package com.lambda.fusion.ai.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.schedule.model.CreateScheduledTask;
import com.lambda.fusion.ai.schedule.model.ScheduledTaskLogPage;
import com.lambda.fusion.ai.schedule.model.ScheduledTaskPage;
import com.lambda.fusion.ai.schedule.model.UpdateScheduledTask;
import com.lambda.fusion.ai.schedule.model.entity.ScheduledTaskLogEntity;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import org.quartz.Trigger.TriggerState;

/**
 * 定时任务服务：任务定义复用 {@code ai_sub_agent}(category=SCHEDULED_TASK)，
 * 生命周期联动 {@code AgentTaskScheduler}。
 *
 * @author Jin
 */
public interface ScheduledTaskService {

    Page<SubAgentEntity> page(ScheduledTaskPage query);

    SubAgentEntity get(String id);

    SubAgentEntity create(CreateScheduledTask dto);

    void update(String id, UpdateScheduledTask dto);

    void delete(String id);

    /** 暂停调度（保留任务定义）。 */
    void pause(String id);

    /** 恢复调度。 */
    void resume(String id);

    /** 立即触发一次（不影响既定调度）。 */
    void trigger(String id);

    /** 查询调度状态。 */
    TriggerState status(String id);

    /** 分页查询该任务的执行记录。 */
    Page<ScheduledTaskLogEntity> pageLogs(String id, ScheduledTaskLogPage query);
}
