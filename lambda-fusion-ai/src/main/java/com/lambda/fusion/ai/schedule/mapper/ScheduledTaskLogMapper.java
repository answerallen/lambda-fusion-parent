package com.lambda.fusion.ai.schedule.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.schedule.model.entity.ScheduledTaskLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行记录 Mapper。执行记录是无 {@code tenant_id} 的全局运维表，租户内查询必须先通过任务实体完成
 * 所有权校验，再以全局唯一的 {@code task_id} 收窄范围。
 *
 * @author Jin
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ScheduledTaskLogMapper extends BaseMapper<ScheduledTaskLogEntity> {}
