package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapModel;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotSanitizer;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.message.Msg;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对话运行协调门面：负责业务 Run 的启动、确认与停止编排。活动实例注册表与容量约束由
 * {@link ChatExecutionInstanceRegistry} 承载；
 * Agent 事件流由执行实例持有，不依赖 SSE 连接生命周期。新运行注册后立即异步订阅 {@code streamEvents}；
 * 同一 {@code (userId, sessionId)} 的核心状态调用由 AgentScope 自身串行保护，上一轮记忆整理等后处理
 * 不阻塞下一轮交互。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ChatExecutionService {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final ChatExecutionInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final ChatExecutionInstanceRegistry registry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chat-run-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 创建协调器。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param messageService 消息服务
     * @param attachmentService 附件服务
     * @param attachmentMessageBuilder 附件消息构造器
     * @param appService 应用服务
     * @param instanceFactory 执行实例工厂
     * @param properties AI 模块配置
     */
    public ChatExecutionService(
            ChatRunStateService runService,
            ChatRunEventStore eventStore,
            ChatMessageService messageService,
            ChatAttachmentService attachmentService,
            ChatAttachmentMessageBuilder attachmentMessageBuilder,
            AppService appService,
            ChatExecutionInstanceFactory instanceFactory,
            AiProperties properties) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.messageService = messageService;
        this.attachmentService = attachmentService;
        this.attachmentMessageBuilder = attachmentMessageBuilder;
        this.appService = appService;
        this.instanceFactory = instanceFactory;
        this.properties = properties;
        this.registry = new ChatExecutionInstanceRegistry(instanceFactory, properties);
    }

    /** 启动本次请求刚创建的运行。幂等旧请求由调用方通过 {@code RunContext.created} 过滤。 */
    public void start(ChatRunEntity run, ChatSessionEntity session) {
        TenantUtils.withTenant(session.getTenantId(), () -> {
            startInTenantContext(run, session);
            return null;
        });
    }

    private void startInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (!ChatRunStatus.RUNNING.name().equals(run.getStatus())) {
            return;
        }
        Optional<ChatExecutionInstance> selected;
        try {
            selected = registry.registerForStartIfCapacity(run, session, scheduler);
        } catch (RuntimeException createFailure) {
            failStart(run, session, createFailure);
            return;
        }
        // 不做跨节点排队或后台扫描；无法在当前请求节点启动时立即给出可重试的失败结果。
        if (selected.isEmpty()) {
            failStart(run, session, new IllegalStateException("当前节点对话运行容量已满"));
            return;
        }
        // 注册成功后排空信号由注册表监听，最终源流结束时按实例身份安全摘除。
        // 只把实际订阅移出请求线程；不等待上一轮的记忆整理、Workspace 审计等后处理。
        ChatExecutionInstance execution = selected.get();
        try {
            scheduler.execute(() -> execution.runInTenant(() -> startExecution(execution)));
        } catch (RuntimeException scheduleFailure) {
            execution.finalizeFailed(
                    ChatRunFailureCode.START_FAILED, ChatRunSnapshotSanitizer.safeMessage(scheduleFailure));
        }
    }

    /**
     * 在规范实例锁内原子地确认并推进到下一阶段。只有上一阶段完整排空并进入待确认态后才允许确认；
     * 锁顺序始终为实例 monitor，再进入 {@code REQUIRES_NEW} 数据库事务。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示来源阶段已经被处理
     */
    public ConfirmTransition confirm(ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        return TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatExecutionInstance execution = registry.get(run.getId());
            if (execution == null) {
                Integer sourcePhaseNo = command == null ? null : command.getPhaseNo();
                if (sourcePhaseNo == null) {
                    throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "phaseNo不能为空");
                }
                if (run.getPhaseNo() > sourcePhaseNo) {
                    return new ConfirmTransition(run, session, false);
                }
                var snapshot = ChatRunSnapshotCodec.decode(run.getSnapshotJson());
                if (!ChatRunStatus.RUNNING.name().equals(run.getStatus())
                        || run.getPhaseNo() < sourcePhaseNo
                        || snapshot.pendingTools().isEmpty()) {
                    throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
                }
                // 进程重启会丢失本地注册表，但 AgentScope ASKING 状态和 Run 快照均已持久化。
                // 用户显式确认时只重建暂停上下文，不恢复旧阶段，也不接管正在执行的工具。
                execution = registry.registerPausedConfirmation(run, session, scheduler);
            }
            return execution.confirm(command);
        });
    }

    /**
     * 从当前 JVM 的内部游标之后订阅运行事件。
     *
     * @param runId 运行标识
     * @param cursor 快照对应的本地游标
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 事件订阅
     */
    public ChatRunEventSubscription subscribe(
            String runId, long cursor, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, cursor, consumer, failureConsumer);
    }

    /**
     * 生成运行的 AG-UI 引导事件。
     *
     * @param run 运行实体
     * @return 引导事件批次
     */
    public AguiBootstrapModel bootstrap(ChatRunEntity run) {
        ChatExecutionInstance execution = registry.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        var snapshot = ChatRunSnapshotCodec.decode(current.getSnapshotJson());
        long cursor = eventStore.latestCursor(current.getId());
        return new AguiBootstrapModel(
                cursor,
                AguiBootstrapEncoder.encode(current, snapshot),
                ChatRunStatus.isTerminal(current.getStatus())
                        || !snapshot.pendingTools().isEmpty()
                        || !eventStore.contains(current.getId()));
    }

    /**
     * 请求停止运行。
     *
     * @param run 运行实体
     * @param session 会话实体
     */
    public void stop(ChatRunEntity run, ChatSessionEntity session) {
        TenantUtils.withTenant(session.getTenantId(), () -> stopInContext(run, session));
    }

    private void stopInContext(ChatRunEntity run, ChatSessionEntity session) {
        ChatRunEntity current = loadCurrent(run);
        if (ChatRunStatus.isTerminal(current.getStatus())) {
            return;
        }
        ChatExecutionInstance execution = registry.get(current.getId());
        if (execution == null) {
            var snapshot = ChatRunSnapshotCodec.decode(current.getSnapshotJson());
            ChatExecutionInstance terminalOnly = createStoppedExecution(
                    current, session, snapshot.pendingTools().isEmpty());
            terminalOnly.requestStop();
            return;
        }
        // 本地活动实例仍按实例 monitor -> 独立数据库事务的顺序协作式停止。
        // 实例锁内先提交 STOPPED，迟到的 Agent 完成结果会被数据库终态拒绝。
        if (!execution.requestStop()) {
            return;
        }
        // 先在锁外发起协作式中断，再以宽限期后的强制停止作为兜底，避免取消回调造成锁重入。
        try {
            execution.interruptAgent();
        } catch (RuntimeException interruptFailure) {
            log.warn("协作式停止Run失败，将等待宽限期后强制停止: runId={}", run.getId(), interruptFailure);
        } finally {
            scheduler.schedule(
                    () -> execution.runInTenant(execution::forceStopIfRunning),
                    properties.getChat().getRun().getStopGraceSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    /** 中断本节点活动运行并关闭本地调度线程池。 */
    @PreDestroy
    public void shutdown() {
        registry.forEachActive(execution -> execution.runInTenant(execution::interruptForShutdown));
        scheduler.shutdown();
    }

    private void startExecution(ChatExecutionInstance execution) {
        ChatRunEntity run = execution.run();
        try {
            ChatMessageEntity userMessage = messageService
                    .findByIdAndSession(run.getUserMessageId(), run.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("Run用户消息不存在: " + run.getId()));
            List<ChatAttachmentEntity> attachments = attachmentService.listByMessageIds(List.of(userMessage.getId()));
            AppEntity app = appService.loadById(execution.session().getAppId());
            Msg msg = attachmentMessageBuilder.buildUserMsg(
                    execution.session(), app, userMessage.getContent(), attachments);
            execution.startPhase(msg);
        } catch (RuntimeException startFailure) {
            execution.finalizeFailed(
                    ChatRunFailureCode.START_FAILED, ChatRunSnapshotSanitizer.safeMessage(startFailure));
        }
    }

    private void failStart(ChatRunEntity run, ChatSessionEntity session, RuntimeException failure) {
        ChatExecutionInstance rejected = instanceFactory.createTerminalOnly(run, session, scheduler);
        rejected.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSnapshotSanitizer.safeMessage(failure));
    }

    private ChatExecutionInstance createStoppedExecution(
            ChatRunEntity run, ChatSessionEntity session, boolean noPendingConfirmation) {
        if (noPendingConfirmation) {
            return instanceFactory.createTerminalOnly(run, session, scheduler);
        }
        try {
            // HITL 已暂停且没有活动工具；加载 AgentScope 状态只为补写拒绝结果，避免 ASKING 污染后续对话。
            return instanceFactory.createPausedConfirmation(run, session, scheduler);
        } catch (RuntimeException stateUnavailable) {
            log.warn("停止Run时无法清理AgentScope待确认状态，仅提交业务终态: runId={}", run.getId(), stateUnavailable);
            return instanceFactory.createTerminalOnly(run, session, scheduler);
        }
    }

    /**
     * 查询最新持久化运行。
     *
     * @param identity 运行标识实体
     * @return 最新运行实体；记录不存在时返回传入实体
     */
    private ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }
}
