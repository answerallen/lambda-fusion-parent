package com.lambda.fusion.ai.schedule;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.ai.AiConstants.ScheduleMode;
import com.lambda.fusion.ai.AiConstants.TaskTriggerType;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.ModelResolver;
import com.lambda.fusion.ai.runtime.ToolkitAssembler;
import com.lambda.fusion.ai.schedule.service.ScheduledTaskLogService;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.scheduler.BaseScheduleAgentTask;
import io.agentscope.extensions.scheduler.ScheduleAgentTask;
import io.agentscope.extensions.scheduler.config.ModelConfig;
import io.agentscope.extensions.scheduler.config.RuntimeAgentConfig;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Trigger.TriggerState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 定时任务调度器：把 {@code ai_sub_agent}(category=SCHEDULED_TASK) 的任务定义转换为
 * AgentScope {@link RuntimeAgentConfig} + {@link ScheduleConfig}，委托 {@link QuartzAgentScheduler}
 * 完成调度 / 持久化 / 暂停恢复取消。调度事实来源归调度器（工程契约 §20.3 单一事实来源），
 * 本类只做「实体 → 配置」转换与生命周期转发。
 *
 * <p><b>租户传递（双通道）</b>：
 * <ul>
 *   <li>DB 层：{@link TenantAwareTask} 装饰器在订阅执行时恢复、结束时清理
 *   {@code TenantContextHolder}（覆盖本地 Mapper/租户插件直连场景）。</li>
 *   <li>Agent 上下文层：{@link #buildSysPrompt} 把 tenantId 注入系统提示词，供 LLM 与走 Dubbo
 *   远程的工具感知归属租户（DB 层 ThreadLocal 对远程调用无效）。</li>
 * </ul>
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AgentTaskScheduler {

    private final QuartzAgentScheduler agentScheduler;
    private final ModelResolver modelResolver;
    private final ToolkitAssembler toolkitAssembler;
    private final ScheduledTaskLogService scheduledTaskLogService;

    /** 调度内唯一名：租户隔离，避免跨租户同名冲突。 */
    public static String scheduleName(String tenantId, String name) {
        return tenantId + ":" + name;
    }

    /** 提交（或重排）任务到调度器；返回带租户上下文恢复的装饰 task。 */
    public ScheduleAgentTask<Msg> scheduleTask(SubAgentEntity entity) {
        validate(entity);
        String name = scheduleName(entity.getTenantId(), entity.getName());
        // 同名重排：先取消旧任务再排新配置（更新场景）
        agentScheduler.cancel(name);

        RuntimeAgentConfig agentConfig = RuntimeAgentConfig.builder()
                .name(name)
                .modelConfig(new EntityModelConfig(entity))
                .sysPrompt(buildSysPrompt(entity))
                .toolkit(buildToolkit(entity))
                .build();
        ScheduleConfig scheduleConfig = buildScheduleConfig(entity);
        Msg input = buildInput(entity);
        ScheduleAgentTask<Msg> task = agentScheduler.schedule(agentConfig, scheduleConfig, input);
        log.info("定时任务已提交调度: name={}, mode={}", name, entity.getScheduleMode());
        return new TenantAwareTask(task, entity.getTenantId());
    }

    public boolean cancel(String tenantId, String name) {
        return agentScheduler.cancel(scheduleName(tenantId, name));
    }

    public boolean pause(String tenantId, String name) {
        return agentScheduler.pause(scheduleName(tenantId, name));
    }

    public boolean resume(String tenantId, String name) {
        return agentScheduler.resume(scheduleName(tenantId, name));
    }

    public TriggerState status(String tenantId, String name) {
        return agentScheduler.getStatus(scheduleName(tenantId, name));
    }

    /**
     * 立即触发一次（不影响既定调度）；未注册时以当前配置临时注册后执行。
     *
     * @deprecated 临时注册路径对 NONE 模式不建 trigger，且异常吞在异步回调里导致假成功；
     * 手动触发请改用 {@link #runOnce(SubAgentEntity)} 同步直跑。
     */
    @Deprecated
    public void triggerNow(SubAgentEntity entity) {
        String name = scheduleName(entity.getTenantId(), entity.getName());
        ScheduleAgentTask<Msg> task = agentScheduler.getScheduledAgent(name);
        if (task == null) {
            task = scheduleTask(entity);
        }
        ScheduleAgentTask<Msg> tenantTask = new TenantAwareTask(task, entity.getTenantId());
        Msg input = buildInput(entity);
        if (input != null) {
            tenantTask.run(input).subscribe();
        } else {
            tenantTask.run().subscribe();
        }
        log.info("定时任务已手动触发: name={}", name);
    }

    /**
     * 手动同步触发一次：不进 Quartz 调度、不污染内存 tasks map，直接以当前实体配置
     * 构建一次性 task 并 {@code block()} 等待执行结束。装配/模型/执行异常在调用线程同步抛出，
     * 由调用方转为业务异常反馈前端，避免「已触发但无效果」的假成功（NONE 模式亦可触发）。
     */
    public void runOnce(SubAgentEntity entity) {
        validate(entity);
        RuntimeAgentConfig agentConfig = RuntimeAgentConfig.builder()
                .name(scheduleName(entity.getTenantId(), entity.getName()))
                .modelConfig(new EntityModelConfig(entity))
                .sysPrompt(buildSysPrompt(entity))
                .toolkit(buildToolkit(entity))
                .build();
        // 一次性任务：空调度配置，不参与持久调度
        BaseScheduleAgentTask task =
                new BaseScheduleAgentTask(agentConfig, ScheduleConfig.builder().build(), agentScheduler);
        ScheduleAgentTask<Msg> tenantTask = new TenantAwareTask(task, entity.getTenantId());
        Msg input = buildInput(entity);
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            Msg result = (input != null)
                    ? tenantTask.run(input).block()
                    : tenantTask.run().block();
            LocalDateTime finishedAt = LocalDateTime.now();
            log.info("定时任务已手动触发执行完成: name={}", entity.getName());
            recordExecution(entity, true, result == null ? null : result.getTextContent(), null, startedAt, finishedAt);
        } catch (Exception e) {
            LocalDateTime finishedAt = LocalDateTime.now();
            log.error("定时任务手动触发执行失败: name={}", entity.getName(), e);
            recordExecution(entity, false, null, e.getMessage(), startedAt, finishedAt);
            if (e instanceof AiBusinessException be) {
                throw be;
            }
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "手动触发执行失败: " + e.getMessage());
        }
    }

    /** 记录一次手动执行（MANUAL）；落库失败不影响执行结果。 */
    private void recordExecution(
            SubAgentEntity entity,
            boolean success,
            String output,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
        scheduledTaskLogService.record(
                entity.getTenantId(),
                entity.getId(),
                entity.getName(),
                TaskTriggerType.MANUAL.name(),
                success,
                output,
                errorMessage,
                startedAt,
                finishedAt);
    }

    // ---------- 实体 → 配置 转换 ----------

    private void validate(SubAgentEntity entity) {
        if (StringUtils.isBlank(entity.getModelId())) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "定时任务必须绑定模型");
        }
        ScheduleMode mode = ScheduleMode.of(entity.getScheduleMode());
        if (mode == ScheduleMode.CRON && StringUtils.isBlank(entity.getCronExpression())) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "CRON 模式必须提供 cron 表达式");
        }
        if (mode == ScheduleMode.FIXED_RATE && (entity.getFixedRate() == null || entity.getFixedRate() <= 0)) {
            throw new AiBusinessException(AiErrorCode.SCHEDULED_TASK_CONFIG_INVALID, "FIXED_RATE 模式必须提供正的固定频率");
        }
    }

    private ScheduleConfig buildScheduleConfig(SubAgentEntity entity) {
        ScheduleConfig.Builder builder = ScheduleConfig.builder();
        ScheduleMode mode = ScheduleMode.of(entity.getScheduleMode());
        if (mode == ScheduleMode.CRON) {
            builder.cron(entity.getCronExpression());
            if (StringUtils.isNotBlank(entity.getZoneId())) {
                builder.zoneId(entity.getZoneId());
            }
        } else if (mode == ScheduleMode.FIXED_RATE) {
            builder.fixedRate(entity.getFixedRate());
        }
        // NONE 保持默认（仅手动触发）
        if (entity.getInitialDelay() != null) {
            builder.initialDelay(entity.getInitialDelay());
        }
        return builder.build();
    }

    /** 系统提示词附带租户标识（Agent 上下文层租户传递），供 LLM 与远程工具感知归属租户。 */
    private String buildSysPrompt(SubAgentEntity entity) {
        return StringUtils.defaultString(entity.getPrompt()) + "\n\n[运行上下文] 归属租户 tenantId=" + entity.getTenantId();
    }

    /** 初始输入消息（可选）；无则触发时不带输入。 */
    private Msg buildInput(SubAgentEntity entity) {
        if (StringUtils.isBlank(entity.getInputMsg())) {
            return null;
        }
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent(entity.getInputMsg())
                .build();
    }

    /** 工具集：注册本地工具 Bean，按 tools_allow 白名单过滤（空=全部本地工具）。 */
    private Toolkit buildToolkit(SubAgentEntity entity) {
        Toolkit toolkit = new Toolkit();
        List<String> allow = entity.getToolsAllow();
        boolean filter = allow != null && !allow.isEmpty();
        for (Object toolBean : toolkitAssembler.getLocalToolBeans()) {
            if (!filter || hasAllowedTool(toolBean, allow)) {
                toolkit.registerTool(toolBean);
            }
        }
        return toolkit;
    }

    /** Bean 是否声明了白名单内的任一 @Tool 方法（工具名取 @Tool.name，空则方法名）。 */
    private boolean hasAllowedTool(Object toolBean, List<String> allow) {
        for (Method method : toolBean.getClass().getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            String toolName = tool.name().isEmpty() ? method.getName() : tool.name();
            if (allow.contains(toolName)) {
                return true;
            }
        }
        return false;
    }

    /** 由 modelId 惰性构建 AgentScope Model 的 ModelConfig 适配（复用 ModelResolver 解密构建）。 */
    private final class EntityModelConfig implements ModelConfig {
        private final SubAgentEntity entity;

        private EntityModelConfig(SubAgentEntity entity) {
            this.entity = entity;
        }

        @Override
        public String getModelName() {
            return entity.getModelId();
        }

        @Override
        public Model createModel() {
            return modelResolver.apply(entity.getModelId());
        }
    }

    /**
     * 租户感知装饰 task：在订阅执行时把归属租户写入 Reactor Context 并设置
     * {@code TenantContextHolder}，执行结束（完成/出错/取消）清理。委派其余方法到底层 task，
     * 不持有独立状态（§20.3 调度事实来源仍归调度器）。
     *
     * <p>{@code run()} 只组装 Mono，真正执行发生在订阅线程（Quartz worker {@code .block()} 所在），
     * 故租户必须在订阅点经 Reactor Context 恢复，而非在组装点（ThreadLocal 已切换）。
     */
    private static final class TenantAwareTask implements ScheduleAgentTask<Msg> {
        private final ScheduleAgentTask<Msg> delegate;
        private final String tenantId;

        private TenantAwareTask(ScheduleAgentTask<Msg> delegate, String tenantId) {
            this.delegate = delegate;
            this.tenantId = tenantId;
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Mono<Msg> run(Msg... msgs) {
            return Mono.defer(() -> {
                        if (StringUtils.isNotBlank(tenantId)) {
                            TenantContextHolder.getInstance().setTenantId(tenantId);
                        }
                        return delegate.run(msgs);
                    })
                    .doFinally(signal -> TenantContextHolder.getInstance().close());
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }
    }
}
