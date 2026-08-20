package com.lambda.fusion.ai.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.schedule.model.entity.ScheduledTaskLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行记录 Mapper。
 *
 * @author Jin
 */
@Mapper
public interface ScheduledTaskLogMapper extends BaseMapper<ScheduledTaskLogEntity> {}
