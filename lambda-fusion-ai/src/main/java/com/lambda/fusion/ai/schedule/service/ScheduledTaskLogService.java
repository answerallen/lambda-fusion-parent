package com.lambda.fusion.ai.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.schedule.model.ScheduledTaskLogPage;
import com.lambda.fusion.ai.schedule.model.entity.ScheduledTaskLogEntity;
import java.time.LocalDateTime;

/**
 * 定时任务执行记录服务：双路径（定时/手动）埋点写入 + 分页查询。
 *
 * @author Jin
 */
public interface ScheduledTaskLogService {

    /**
     * 记录一次执行（调度线程安全：内部恢复租户上下文后落库）。
     *
     * @param tenantId 归属租户
     * @param taskId 定时任务ID
     * @param taskName 任务名快照
     * @param triggerType 触发方式（SCHEDULED/MANUAL）
     * @param success 是否成功
     * @param output Agent 最终输出全文（可空，定时路径暂为空）
     * @param errorMessage 失败信息（成功时为空）
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    void record(
            String tenantId,
            String taskId,
            String taskName,
            String triggerType,
            boolean success,
            String output,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt);

    /** 按任务分页查询执行记录。 */
    Page<ScheduledTaskLogEntity> page(ScheduledTaskLogPage query);
}
