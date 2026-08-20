package com.lambda.fusion.ai.schedule.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.AiConstants.ScheduleMode;
import com.lambda.fusion.ai.AiConstants.SubAgentCategory;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.schedule.AgentTaskScheduler;
import com.lambda.fusion.ai.schedule.model.CreateScheduledTask;
import com.lambda.fusion.ai.schedule.model.ScheduledTaskPage;
import com.lambda.fusion.ai.schedule.model.UpdateScheduledTask;
import com.lambda.fusion.ai.schedule.service.ScheduledTaskService;
import com.lambda.fusion.ai.subagent.mapper.SubAgentMapper;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Trigger.TriggerState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务服务实现：任务定义落 {@code ai_sub_agent}(category=SCHEDULED_TASK)，
 * 调度经 {@link AgentTaskScheduler} 联动；调度状态由调度器单一解释（§20.3）。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private final SubAgentMapper subAgentMapper;
    private final LlmModelService llmModelService;
    private final AgentTaskScheduler agentTaskScheduler;

    @Override
    public Page<SubAgentEntity> page(ScheduledTaskPage query) {
        return subAgentMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public SubAgentEntity get(String id) {
        return requireTask(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubAgentEntity create(CreateScheduledTask dto) {
        ensureNameUnique(dto.getName(), null);
        validateModel(dto.getModelId());
        validateSchedule(dto.getScheduleMode(), dto.getCronExpression(), dto.getFixedRate());

        SubAgentEntity entity = new SubAgentEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setTenantId(AuthUtils.getTenantId());
        entity.setName(dto.getName());
        entity.setCategory(SubAgentCategory.SCHEDULED_TASK.getCode());
        // description 列 NOT NULL 无默认值(对子代理是路由依据必填)；定时任务不参与路由,
        // 用备注或任务名兜底占位,避免 insert 违反非空约束
        entity.setDescription(StringUtils.defaultIfBlank(dto.getRemark(), dto.getName()));
        entity.setPrompt(dto.getPrompt());
        entity.setModelId(dto.getModelId());
        entity.setScheduleMode(normalizeMode(dto.getScheduleMode()));
        entity.setCronExpression(dto.getCronExpression());
        entity.setFixedRate(dto.getFixedRate());
        entity.setInitialDelay(dto.getInitialDelay());
        entity.setZoneId(dto.getZoneId());
        entity.setInputMsg(dto.getInputMsg());
        entity.setToolsAllow(dto.getToolsAllow());
        entity.setTemperature(dto.getTemperature());
        entity.setTopP(dto.getTopP());
        entity.setSteps(dto.getSteps());
        entity.setRemark(dto.getRemark());
        // 定时任务不参与主 Agent 路由：路由态恒禁用，调度态独立
        entity.setEnabled(Boolean.FALSE);
        entity.setScheduleEnabled(Boolean.TRUE.equals(dto.getScheduleEnabled()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.insert(entity);

        if (Boolean.TRUE.equals(entity.getScheduleEnabled())) {
            agentTaskScheduler.scheduleTask(entity);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateScheduledTask dto) {
        SubAgentEntity entity = requireTask(id);
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(entity.getName())) {
            ensureNameUnique(dto.getName(), id);
            entity.setName(dto.getName());
        }
        if (dto.getPrompt() != null) {
            entity.setPrompt(dto.getPrompt());
        }
        if (StringUtils.isNotBlank(dto.getModelId()) && !dto.getModelId().equals(entity.getModelId())) {
            validateModel(dto.getModelId());
            entity.setModelId(dto.getModelId());
        }
        if (StringUtils.isNotBlank(dto.getScheduleMode())) {
            entity.setScheduleMode(normalizeMode(dto.getScheduleMode()));
        }
        if (dto.getCronExpression() != null) {
            entity.setCronExpression(dto.getCronExpression());
        }
        if (dto.getFixedRate() != null) {
            entity.setFixedRate(dto.getFixedRate());
        }
        if (dto.getInitialDelay() != null) {
            entity.setInitialDelay(dto.getInitialDelay());
        }
        if (dto.getZoneId() != null) {
            entity.setZoneId(dto.getZoneId());
        }
        if (dto.getInputMsg() != null) {
            entity.setInputMsg(dto.getInputMsg());
        }
        if (dto.getToolsAllow() != null) {
            entity.setToolsAllow(dto.getToolsAllow());
        }
        if (dto.getTemperature() != null) {
            entity.setTemperature(dto.getTemperature());
        }
        if (dto.getTopP() != null) {
            entity.setTopP(dto.getTopP());
        }
        if (dto.getSteps() != null) {
            entity.setSteps(dto.getSteps());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        if (dto.getScheduleEnabled() != null) {
            entity.setScheduleEnabled(dto.getScheduleEnabled());
        }
        validateSchedule(entity.getScheduleMode(), entity.getCronExpression(), entity.getFixedRate());
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.updateById(entity);

        reschedule(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SubAgentEntity entity = requireTask(id);
        subAgentMapper.deleteById(id);
        agentTaskScheduler.cancel(entity.getTenantId(), entity.getName());
    }

    /**
     * 暂停调度：回写 {@code schedule_enabled=false} 作为业务事实来源；调度器暂停仅作运行时联动，
     * 内存调度器下任务未注册（返回 false）不视为失败，仅记录告警。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(String id) {
        SubAgentEntity entity = requireTask(id);
        boolean paused = agentTaskScheduler.pause(entity.getTenantId(), entity.getName());
        if (!paused) {
            log.warn("定时任务暂停时调度器未找到活动任务(可能未注册/已重启): id={}, name={}", id, entity.getName());
        }
        entity.setScheduleEnabled(Boolean.FALSE);
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.updateById(entity);
    }

    /**
     * 恢复调度：回写 {@code schedule_enabled=true} 并按当前配置重排（scheduleTask 幂等先取消再排），
     * 确保内存调度器下任务被重新注册，而非仅 resume 一个可能不存在的壳。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(String id) {
        SubAgentEntity entity = requireTask(id);
        entity.setScheduleEnabled(Boolean.TRUE);
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.updateById(entity);
        agentTaskScheduler.scheduleTask(entity);
    }

    /** 手动触发：同步直跑，装配/执行异常抛业务异常反馈前端，不假成功。 */
    @Override
    public void trigger(String id) {
        SubAgentEntity entity = requireTask(id);
        agentTaskScheduler.runOnce(entity);
    }

    @Override
    public TriggerState status(String id) {
        SubAgentEntity entity = requireTask(id);
        return agentTaskScheduler.status(entity.getTenantId(), entity.getName());
    }

    /** 按启用态重排：启用→提交调度，禁用→取消调度。 */
    private void reschedule(SubAgentEntity entity) {
        if (Boolean.TRUE.equals(entity.getScheduleEnabled())) {
            agentTaskScheduler.scheduleTask(entity);
        } else {
            agentTaskScheduler.cancel(entity.getTenantId(), entity.getName());
        }
    }

    /** 按主键加载并限定为定时任务分类；不存在或非定时任务抛业务异常。 */
    private SubAgentEntity requireTask(String id) {
        SubAgentEntity entity = subAgentMapper.selectOne(new LambdaQueryWrapper<SubAgentEntity>()
                .eq(SubAgentEntity::getId, id)
                .eq(SubAgentEntity::getCategory, SubAgentCategory.SCHEDULED_TASK.getCode()));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String name, String excludeId) {
        boolean exists = subAgentMapper.exists(new LambdaQueryWrapper<SubAgentEntity>()
                .eq(SubAgentEntity::getTenantId, AuthUtils.getTenantId())
                .eq(SubAgentEntity::getCategory, SubAgentCategory.SCHEDULED_TASK.getCode())
                .eq(SubAgentEntity::getName, name)
                .ne(excludeId != null, SubAgentEntity::getId, excludeId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_NAME_EXISTS, name);
        }
    }

    private void validateModel(String modelId) {
        LlmModelEntity model = llmModelService.loadById(modelId);
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.SUB_AGENT_MODEL_INVALID, modelId);
        }
    }

    private void validateSchedule(String mode, String cron, Long fixedRate) {
        ScheduleMode m = ScheduleMode.of(mode);
        if (m == ScheduleMode.CRON && StringUtils.isBlank(cron)) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "CRON 模式必须提供 cron 表达式");
        }
        if (m == ScheduleMode.FIXED_RATE && (fixedRate == null || fixedRate <= 0)) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "FIXED_RATE 模式必须提供正的固定频率");
        }
    }

    private String normalizeMode(String mode) {
        ScheduleMode m = ScheduleMode.of(mode);
        if (m == null) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "非法调度模式: " + mode);
        }
        return m.name();
    }
}
