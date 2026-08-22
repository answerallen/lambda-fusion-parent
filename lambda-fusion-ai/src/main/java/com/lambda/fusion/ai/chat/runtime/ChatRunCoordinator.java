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
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstance;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstanceFactory;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.registry.ChatRunInstanceRegistry;
import com.lambda.fusion.ai.chat.runtime.registry.ChatRunMaintenanceScheduler;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
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
 * {@link ChatRunInstanceRegistry} 承载，定时维护与确认超时扫描由 {@link ChatRunMaintenanceScheduler} 承载；
 * Agent 事件流由执行实例持有，不依赖 SSE 连接生命周期。新运行注册后立即异步订阅 {@code streamEvents}；
 * 同一 {@code (userId, sessionId)} 的核心状态调用由 AgentScope 自身串行保护，上一轮记忆整理等后处理
 * 不阻塞下一轮交互。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ChatRunCoordinator {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final ChatRunInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final ChatRunInstanceRegistry registry;
    private final ChatRunMaintenanceScheduler maintenanceScheduler;
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
    public ChatRunCoordinator(
            ChatRunStateService runService,
            ChatRunEventStore eventStore,
            ChatMessageService messageService,
            ChatAttachmentService attachmentService,
            ChatAttachmentMessageBuilder attachmentMessageBuilder,
            AppService appService,
            ChatRunInstanceFactory instanceFactory,
            AiProperties properties) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.messageService = messageService;
        this.attachmentService = attachmentService;
        this.attachmentMessageBuilder = attachmentMessageBuilder;
        this.appService = appService;
        this.instanceFactory = instanceFactory;
        this.properties = properties;
        this.registry = new ChatRunInstanceRegistry(instanceFactory, properties);
        this.maintenanceScheduler =
                new ChatRunMaintenanceScheduler(scheduler, eventStore, registry, runService, this::startIfCreated);
    }

    /**
     * 启动处于创建状态的运行。
     *
     * @param run 运行实体
     * @param session 会话实体
     */
    public void startIfCreated(ChatRunEntity run, ChatSessionEntity session) {
        TenantUtils.withTenant(session.getTenantId(), () -> {
            startIfCreatedInTenantContext(run, session);
            return null;
        });
    }

    void startIfCreated(ChatRunEntity run) {
        startIfCreated(run, loadSession(run));
    }

    private void startIfCreatedInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (!ChatRunStatus.CREATED.name().equals(run.getStatus())) {
            return;
        }
        Optional<ChatRunInstanceRegistry.StartRegistration> selected;
        try {
            selected = registry.restoreForStartIfCapacity(run, session, scheduler);
        } catch (RuntimeException restoreFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler);
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed(
                        ChatRunFailureCode.START_FAILED, ChatRunDataSanitizer.safeMessage(restoreFailure));
            }
            return;
        }
        // 本进程容量已满时跳过（不改状态），容量释放后由维护任务再次尝试。
        if (selected.isEmpty()) {
            return;
        }
        ChatRunInstanceRegistry.StartRegistration registration = selected.get();
        if (registration.registered()) {
            // 注册成功后排空信号由注册表监听，最终源流结束时按实例身份安全摘除。
            // 只把实际订阅移出请求线程；不等待上一轮的记忆整理、Workspace 审计等后处理。
            ChatRunInstance execution = registration.execution();
            scheduler.execute(() -> execution.runInTenant(() -> startCreated(execution)));
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
            ChatRunInstance execution = registry.selectOrRestore(run, session, scheduler);
            return execution.confirm(command);
        });
    }

    /**
     * 订阅指定序号之后的运行事件。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 事件订阅
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, afterSeq, consumer, failureConsumer);
    }

    /**
     * 生成运行的 AG-UI 引导事件。
     *
     * @param run 运行实体
     * @return 引导事件批次
     */
    public AguiBootstrap bootstrap(ChatRunEntity run) {
        ChatRunInstance execution = registry.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new AguiBootstrap(
                highWatermark,
                AguiBootstrapEncoder.encode(
                        current, ChatRunSnapshotCodec.decode(current.getSnapshotJson()), highWatermark),
                ChatRunStatus.isTerminal(current.getStatus())
                        || ChatRunStatus.AWAITING_CONFIRM.name().equals(current.getStatus())
                        || !eventStore.contains(run.getId()));
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
        if (ChatRunStatus.isTerminal(run.getStatus())) {
            return;
        }
        ChatRunInstance execution = registry.get(run.getId());
        if (execution == null) {
            if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                // 待确认阶段没有活动源流；只借助 AgentScope 持久化状态闭合未决工具，不执行 interrupt。
                instanceFactory
                        .restoreConfirmationFinalizer(run, session, scheduler)
                        .requestStop();
            } else {
                // 当前 JVM 没有活动源流时，只结束业务 Run；不恢复 Agent 来伪装跨节点中断。
                instanceFactory.restoreFinalizer(run, session, scheduler).requestStop();
            }
            return;
        }
        // 本地活动实例仍按实例 monitor -> 独立数据库事务的顺序协作式停止。
        // 在实例锁内同时判断运行状态并迁移到 STOPPING，消除检查与启动新源流之间的竞态窗口。
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

    /** 启动定时维护任务。供 {@link ChatRunRecoveryListener} 编排调用。 */
    void scheduleMaintenance() {
        maintenanceScheduler.schedule();
    }

    /** 中断本节点活动运行并关闭定时维护线程池。 */
    @PreDestroy
    public void shutdown() {
        registry.forEachActive(execution -> execution.runInTenant(execution::interruptForShutdown));
        scheduler.shutdown();
    }

    private void startCreated(ChatRunInstance execution) {
        ChatRunEntity run = execution.run();
        if (!runService.claimCreated(run)) {
            // 运行在调度或认领期间已被并发方终结，本实例未建立源流；完成排空信号让注册表摘除实例。
            execution.releaseNeverStarted();
            return;
        }
        run.setStatus(ChatRunStatus.RUNNING.name());
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
            execution.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunDataSanitizer.safeMessage(startFailure));
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

    private ChatSessionEntity loadSession(ChatRunEntity run) {
        return runService.loadSession(run);
    }
}
