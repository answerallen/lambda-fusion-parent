package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.mapper.ChatRunMapper;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ChatRunFinalizationCommand;
import com.lambda.fusion.ai.chat.model.ChatRunFinalizationResult;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.RunContext;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.service.AbstractCrudService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话运行的唯一持久化服务，负责幂等创建、状态迁移、检查点和终态提交。该实现同时服务于两个调用面：
 * {@link ChatRunService} 面向 HTTP 编排并校验会话归属，{@link ChatRunStateService} 面向执行状态机并独立提交迁移。
 * 状态机为 {@code CREATED -> RUNNING <-> AWAITING_CONFIRM -> STOPPING -> COMPLETED/STOPPED/FAILED}。
 *
 * <p>创建请求以 {@code clientRequestId} 去重，并通过 {@code requestHash} 校验请求内容；状态迁移采用带前置条件的
 * UPDATE 实现 CAS，只有影响一行时才成功。检查点和状态迁移使用 {@link Propagation#REQUIRES_NEW} 独立提交，
 * 保证执行流程后续失败时仍可根据已保存状态恢复。
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatRunServiceImpl extends AbstractCrudService<ChatRunEntity, ChatRun, ChatRunMapper>
        implements ChatRunService, ChatRunStateService {

    private final ChatRunMapper runMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final AppService appService;
    private final com.lambda.fusion.ai.chat.runtime.ChatRunNodeIdentity nodeIdentity;
    private final AiProperties properties;

    /** 幂等创建或加载运行；同一请求 ID 复用已有记录，否则在会话无活动运行时创建并保存用户消息。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RunContext createOrLoad(String sessionId, SendMessage message) {
        ChatSessionEntity session = sessionService.loadOwnedForUpdate(sessionId);
        String requestHash = hashRequest(message);
        // 命中同一 clientRequestId 时复用已有运行，并继续校验请求内容是否一致。
        ChatRunEntity existing = findByRequest(sessionId, message.getClientRequestId());
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            return new RunContext(existing, session);
        }
        appService.loadAvailable(session.getAppId());
        // 同一会话只允许一个活动运行，避免多个执行上下文竞争状态槽。
        ChatRunEntity active = findActive(sessionId);
        if (active != null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_ALREADY_ACTIVE, active.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        ChatRunEntity run = new ChatRunEntity();
        run.setId(IdUtil.getSnowflakeNextIdStr());
        run.setSessionId(sessionId);
        run.setClientRequestId(message.getClientRequestId());
        run.setRequestHash(requestHash);
        run.setStatus(ChatRunStatus.CREATED.name());
        run.setPhaseNo(1);
        run.setAguiRunId(newAguiRunId());
        run.setSnapshotSeq(0L);
        // 标记创建节点为执行节点，供「本机活跃 vs 远程活跃」判定。
        run.setExecutorInstanceId(nodeIdentity.instanceId());
        runMapper.insert(run);

        // 先保存用户消息并绑定附件，再回填消息 ID 和初始空快照。
        ChatMessageEntity userMessage =
                messageService.saveUserMessage(session, StringUtils.defaultString(message.getContent()));
        if (message.getAttachmentIds() != null) {
            attachmentService.bindToMessage(
                    session, message.getAttachmentIds().stream().distinct().toList(), userMessage.getId());
        }
        run.setUserMessageId(userMessage.getId());
        run.setSnapshotJson(
                ChatRunSnapshotCodec.encode(ChatRunSnapshot.empty(run.getId(), run.getAguiRunId(), run.getPhaseNo())));
        runMapper.updateById(run);
        // 刷新会话最近消息时间，用于会话列表排序。
        sessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getId, sessionId)
                        .set(ChatSessionEntity::getLastMessageAt, now)
                        .set(ChatSessionEntity::getUpdatedAt, now));
        // 重新读取记录，返回插入和更新后的最终持久化状态。
        ChatRunEntity persisted = runMapper.selectById(run.getId());
        if (persisted == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_NOT_FOUND, run.getId());
        }
        return new RunContext(persisted, session);
    }

    /** 返回当前用户拥有的会话下唯一活跃 Run（无则空）。 */
    @Override
    public Optional<ChatRun> getActiveOwned(String sessionId) {
        sessionService.loadOwned(sessionId);
        return Optional.ofNullable(findActive(sessionId)).map(this::toRunView);
    }

    /** 按 runId 载入当前用户拥有的 Run 视图。 */
    @Override
    public ChatRun getOwned(String sessionId, String runId) {
        return toRunView(loadOwned(sessionId, runId).run());
    }

    /** 载入当前用户拥有的 Run 及其会话（不存在则抛 {@code CHAT_RUN_NOT_FOUND}）。 */
    @Override
    public RunContext loadOwned(String sessionId, String runId) {
        ChatSessionEntity session = sessionService.loadOwned(sessionId);
        ChatRunEntity run = runMapper.selectOne(ownedQuery(sessionId, runId));
        if (run == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_NOT_FOUND, runId);
        }
        return new RunContext(run, session);
    }

    /**
     * 在独立事务中推进用户确认。方法复核资源归属并校验阶段，以
     * {@code status=AWAITING_CONFIRM, phaseNo=sourcePhaseNo} 为前置条件执行 CAS 迁移。
     * 运行已越过来源阶段时按重复确认返回 {@code resumed=false}；运行尚未到达该阶段时按过期命令处理。
     * 确认内容由执行实例校验，本方法只负责权威状态迁移。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ConfirmTransition advanceConfirmation(
            ChatRunEntity identity, ChatSessionEntity expectedSession, int sourcePhaseNo) {
        // 在行锁内复核会话所有权和运行归属，使并发确认串行化。
        ChatSessionEntity session = sessionMapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, identity.getSessionId())
                .eq(ChatSessionEntity::getUserId, expectedSession.getUserId())
                .last("FOR UPDATE"));
        if (session == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_NOT_FOUND, identity.getId());
        }
        ChatRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getId, identity.getId())
                .eq(ChatRunEntity::getSessionId, session.getId())
                .last("FOR UPDATE"));
        if (run == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_NOT_FOUND, identity.getId());
        }
        // 当前阶段号更大表示确认已处理，幂等返回；更小表示命令尚不适用于当前阶段。
        if (!Objects.equals(run.getPhaseNo(), sourcePhaseNo)) {
            if (run.getPhaseNo() > sourcePhaseNo) {
                return new ConfirmTransition(run, session, false);
            }
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }
        // 只有待确认态可被确认，否则状态冲突。
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }

        int targetPhase = run.getPhaseNo() + 1;
        String targetAguiRunId = newAguiRunId();
        LocalDateTime dbNow = dbNow();
        // 以待确认状态和阶段号为前置条件迁移到 RUNNING；并发更新未命中时报告状态冲突。
        // 确认在任一节点发起新的一次 AgentScope 调用（非迁移旧调用），故标记本节点为执行节点并起跳心跳。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .eq(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                        .eq(ChatRunEntity::getPhaseNo, sourcePhaseNo)
                        .set(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                        .set(ChatRunEntity::getPhaseNo, targetPhase)
                        .set(ChatRunEntity::getAguiRunId, targetAguiRunId)
                        .set(ChatRunEntity::getAwaitConfirmDeadlineAt, null)
                        .set(ChatRunEntity::getExecutorInstanceId, nodeIdentity.instanceId())
                        .set(ChatRunEntity::getHeartbeatAt, dbNow)
                        .set(ChatRunEntity::getUpdatedAt, dbNow));
        if (changed != 1) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getId());
        }
        // 用迁移后的值刷新内存对象，供调用方继续执行。
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setPhaseNo(targetPhase);
        run.setAguiRunId(targetAguiRunId);
        run.setExecutorInstanceId(nodeIdentity.instanceId());
        run.setHeartbeatAt(dbNow);
        return new ConfirmTransition(run, session, true);
    }

    /** CAS 认领新建 Run：{@code CREATED -> RUNNING}，同时标记执行节点并起跳心跳；已被并发认领则返回 false。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claimCreated(ChatRunEntity run) {
        LocalDateTime dbNow = dbNow();
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getStatus, ChatRunStatus.CREATED.name())
                                .set(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                                .set(ChatRunEntity::getStartedAt, dbNow)
                                .set(ChatRunEntity::getExecutorInstanceId, nodeIdentity.instanceId())
                                .set(ChatRunEntity::getHeartbeatAt, dbNow)
                                .set(ChatRunEntity::getUpdatedAt, dbNow))
                == 1;
    }

    /**
     * 更新本节点持有 Run 的执行心跳：仅当该 Run 仍标记由本节点执行时落库，避免失效节点复活后覆盖。
     *
     * @param run 运行实体（携带 id）
     * @return 心跳写入成功返回 true；该 Run 已不由本节点执行返回 false
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean heartbeat(ChatRunEntity run) {
        LocalDateTime dbNow = dbNow();
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getExecutorInstanceId, nodeIdentity.instanceId())
                                .notIn(ChatRunEntity::getStatus, ChatRunStatus.terminalNames())
                                .set(ChatRunEntity::getHeartbeatAt, dbNow))
                == 1;
    }

    /** CAS 写检查点：在非终态下更新快照与序号，成功返回 true；否则拒绝写入。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean checkpoint(ChatRunEntity run, ChatRunSnapshot snapshot, long seq) {
        LocalDateTime now = LocalDateTime.now();
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .notIn(ChatRunEntity::getStatus, ChatRunStatus.terminalNames())
                                .set(ChatRunEntity::getSnapshotJson, ChatRunSnapshotCodec.encode(snapshot))
                                .set(ChatRunEntity::getSnapshotSeq, seq)
                                .set(ChatRunEntity::getUpdatedAt, now))
                == 1;
    }

    /** CAS 进入待确认：{@code RUNNING -> AWAITING_CONFIRM}，同事务持久化快照并停跳心跳（清空 heartbeat）。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean awaitConfirm(ChatRunEntity run, ChatRunSnapshot snapshot, long seq, LocalDateTime deadline) {
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                                .set(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                                .set(ChatRunEntity::getAwaitConfirmDeadlineAt, deadline)
                                .set(ChatRunEntity::getSnapshotJson, ChatRunSnapshotCodec.encode(snapshot))
                                .set(ChatRunEntity::getSnapshotSeq, seq)
                                // 进入待确认即停跳心跳：无执行节点活动，避免被失效扫描误判；确认/超时路径自行收敛。
                                .set(ChatRunEntity::getHeartbeatAt, null)
                                .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()))
                == 1;
    }

    /** CAS 请求停止：从 {@code CREATED/RUNNING/AWAITING_CONFIRM} 迁移到 {@code STOPPING}，成功返回 true。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean requestStopping(ChatRunEntity run) {
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .in(
                                        ChatRunEntity::getStatus,
                                        ChatRunStatus.CREATED.name(),
                                        ChatRunStatus.RUNNING.name(),
                                        ChatRunStatus.AWAITING_CONFIRM.name())
                                .set(ChatRunEntity::getStatus, ChatRunStatus.STOPPING.name())
                                .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()))
                == 1;
    }

    /** CAS 确认超时：对已超截止时间的 {@code AWAITING_CONFIRM} 迁移到 {@code STOPPING}，成功返回 true。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline) {
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                                .le(ChatRunEntity::getAwaitConfirmDeadlineAt, deadline)
                                .set(ChatRunEntity::getStatus, ChatRunStatus.STOPPING.name())
                                .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()))
                == 1;
    }

    /**
     * 在独立事务中提交运行终态、助手消息和最终快照。已有终态时返回 {@code committed=false} 并携带原结果。
     * {@code STOPPING} 优先于调用方给出的目标状态，最终统一写为 {@code STOPPED}，原因设为 {@code USER_STOP}。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ChatRunFinalizationResult finalizeExecution(ChatRunEntity identity, ChatRunFinalizationCommand command) {
        // 悲观锁定会话和运行，使终结过程与其他状态迁移串行执行。
        ChatSessionEntity session = sessionMapper.selectForUpdate(identity.getSessionId());
        if (session == null) {
            throw new IllegalStateException("Run会话不存在，无法终结: " + identity.getSessionId());
        }
        ChatRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getId, identity.getId())
                .last("FOR UPDATE"));
        if (run == null) {
            throw new IllegalStateException("Run不存在，无法终结: " + identity.getId());
        }
        // 已终态：幂等返回 committed=false，携带既有终态信息。
        if (ChatRunStatus.isTerminal(run.getStatus())) {
            return new ChatRunFinalizationResult(
                    false, run.getStatus(), run.getFinishReason(), run.getErrorCode(), run.getErrorMessage());
        }

        ChatRunStatus targetStatus = command.targetStatus();
        ChatRunSnapshot snapshot = command.snapshot();
        String toolCallJson = command.toolCallJson();
        long lastSeq = command.lastSeq();
        // STOPPING 优先，避免已接受的停止请求被完成或失败结果覆盖。
        boolean stopWon =
                ChatRunStatus.STOPPING.name().equals(run.getStatus()) && targetStatus != ChatRunStatus.STOPPED;
        ChatRunStatus finalStatus = stopWon ? ChatRunStatus.STOPPED : targetStatus;
        ChatRunFinishReason finalReason = stopWon ? ChatRunFinishReason.USER_STOP : command.finishReason();
        ChatRunFailureCode finalErrorCode = finalStatus == ChatRunStatus.STOPPED ? null : command.errorCode();
        String finalErrorMessage = finalStatus == ChatRunStatus.STOPPED ? null : command.errorMessage();

        // 仅 COMPLETED 或仍有正文/工具调用时才落库助手消息。
        ChatMessageEntity assistant = null;
        if (finalStatus == ChatRunStatus.COMPLETED || !snapshot.text().isBlank() || toolCallJson != null) {
            assistant = messageService.saveAssistantMessage(session, snapshot.text(), toolCallJson);
        }
        LocalDateTime now = LocalDateTime.now();
        // 仅允许非终态记录写入终态；更新未命中表示其他并发方已先完成终结。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .notIn(ChatRunEntity::getStatus, ChatRunStatus.terminalNames())
                        .set(ChatRunEntity::getStatus, finalStatus.name())
                        .set(ChatRunEntity::getFinishReason, finalReason == null ? null : finalReason.name())
                        .set(ChatRunEntity::getAssistantMessageId, assistant == null ? null : assistant.getId())
                        .set(ChatRunEntity::getAwaitConfirmDeadlineAt, null)
                        .set(ChatRunEntity::getSnapshotJson, ChatRunSnapshotCodec.encode(snapshot))
                        .set(ChatRunEntity::getSnapshotSeq, lastSeq)
                        .set(ChatRunEntity::getErrorCode, finalErrorCode == null ? null : finalErrorCode.name())
                        .set(ChatRunEntity::getErrorMessage, finalErrorMessage)
                        // 终态停跳心跳：运行已结束，不再参与失效扫描。
                        .set(ChatRunEntity::getHeartbeatAt, null)
                        .set(ChatRunEntity::getFinishedAt, now)
                        .set(ChatRunEntity::getUpdatedAt, now));
        if (changed != 1) {
            throw new IllegalStateException("Run终结状态更新失败: " + run.getId());
        }
        // 刷新会话最近消息时间。
        sessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getId, run.getSessionId())
                        .eq(ChatSessionEntity::getUserId, session.getUserId())
                        .set(ChatSessionEntity::getLastMessageAt, now)
                        .set(ChatSessionEntity::getUpdatedAt, now));
        return new ChatRunFinalizationResult(
                true,
                finalStatus.name(),
                finalReason == null ? null : finalReason.name(),
                finalErrorCode == null ? null : finalErrorCode.name(),
                finalErrorMessage);
    }

    /** 在终态下补写最终快照与序号；行已被清理时容忍不抛，行仍在却写失败则异常。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordTerminalSeq(ChatRunEntity run, ChatRunSnapshot snapshot, long seq) {
        // 仅终态可写最终游标；CAS 以终态为前置条件。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .in(ChatRunEntity::getStatus, ChatRunStatus.terminalNames())
                        .set(ChatRunEntity::getSnapshotJson, ChatRunSnapshotCodec.encode(snapshot))
                        .set(ChatRunEntity::getSnapshotSeq, seq)
                        .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()));
        // 写失败但行仍在则异常；行已被清理则容忍（不抛）。
        if (changed != 1 && runMapper.selectById(run.getId()) != null) {
            throw new IllegalStateException("Run终态游标写入失败: " + run.getId());
        }
    }

    /** 按运行 ID 加载实体，不校验用户归属，仅供内部执行状态机使用。 */
    @Override
    public ChatRunEntity loadCurrent(String runId) {
        return runMapper.selectById(runId);
    }

    /** 按 Run 携带的 sessionId 载入会话（不存在则抛异常）。 */
    @Override
    public ChatSessionEntity loadSession(ChatRunEntity run) {
        ChatSessionEntity session = sessionMapper.selectById(run.getSessionId());
        if (session == null) {
            throw new IllegalStateException("Run会话不存在: " + run.getSessionId());
        }
        return session;
    }

    /** 查询处于 {@code CREATED}、等待调度器认领的运行。 */
    @Override
    public List<ChatRunEntity> listCreated() {
        return runMapper.selectList(
                new LambdaQueryWrapper<ChatRunEntity>().eq(ChatRunEntity::getStatus, ChatRunStatus.CREATED.name()));
    }

    /** 查询创建时间早于阈值、仍未被认领的 {@code CREATED} 运行（全节点长期满载时按调度超时收敛）。 */
    @Override
    public List<ChatRunEntity> listStaleCreated(LocalDateTime createdBefore) {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getStatus, ChatRunStatus.CREATED.name())
                .le(ChatRunEntity::getCreatedAt, createdBefore));
    }

    /** 查询执行节点心跳已超时的中断态运行（RUNNING/STOPPING），供失效收敛标记。 */
    @Override
    public List<ChatRunEntity> listExpiredHeartbeatRuns(LocalDateTime timedOutBefore) {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .in(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name(), ChatRunStatus.STOPPING.name())
                .le(ChatRunEntity::getHeartbeatAt, timedOutBefore));
    }

    /** 查询截止时间不晚于给定时刻的待确认运行。 */
    @Override
    public List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline) {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                .le(ChatRunEntity::getAwaitConfirmDeadlineAt, deadline));
    }

    /**
     * 查询心跳已超时（或本就无心跳）的中断态运行（RUNNING/STOPPING/AWAITING_CONFIRM），供启动恢复逐个收敛。
     *
     * <p>降级模型下不做跨节点接管：本进程重启后其持有的中断态 Run 心跳随之停跳并随启动耗时超时而命中本查询，
     * 由本节点终结（或保留待确认）；仍存活节点的 Run 心跳新鲜，不会被本查询命中，避免滚动发布误终结。
     * 终态迁移经 DB 终态 CAS 幂等，并发恢复仅一方生效。
     *
     * @param timedOutBefore 心跳超时阈值；{@code heartbeat_at} 为空或早于该时刻视为执行节点已失效
     * @return 心跳超时/无心跳的中断态运行
     */
    @Override
    public List<ChatRunEntity> listInterruptedOnRestart(LocalDateTime timedOutBefore) {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .in(
                        ChatRunEntity::getStatus,
                        ChatRunStatus.RUNNING.name(),
                        ChatRunStatus.STOPPING.name(),
                        ChatRunStatus.AWAITING_CONFIRM.name())
                .and(w -> w.isNull(ChatRunEntity::getHeartbeatAt)
                        .or()
                        .le(ChatRunEntity::getHeartbeatAt, timedOutBefore)));
    }

    /** 实体转视图，并在待确认态填充待确认工具列表。 */
    private ChatRun toRunView(ChatRunEntity entity) {
        ChatRun view = toVO(entity);
        ChatRunSnapshot snapshot = ChatRunSnapshotCodec.decode(entity.getSnapshotJson());
        view.setPendingConfirm(
                ChatRunStatus.AWAITING_CONFIRM.name().equals(entity.getStatus())
                        ? snapshot.pendingTools().stream()
                                .map(tool -> new ChatRun.PendingTool(tool.toolCallId(), tool.toolCallName()))
                                .toList()
                        : List.of());
        return view;
    }

    /** 按会话 ID 和客户端请求 ID 查找已有运行，用于幂等去重。 */
    private ChatRunEntity findByRequest(String sessionId, String requestId) {
        return runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getSessionId, sessionId)
                .eq(ChatRunEntity::getClientRequestId, requestId));
    }

    /** 查找会话内非终态的活跃 Run。 */
    private ChatRunEntity findActive(String sessionId) {
        return runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getSessionId, sessionId)
                .notIn(ChatRunEntity::getStatus, ChatRunStatus.terminalNames()));
    }

    /** 使用运行 ID 和会话 ID 构造双键查询，确保运行归属目标会话。 */
    private static LambdaQueryWrapper<ChatRunEntity> ownedQuery(String sessionId, String runId) {
        return new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getId, runId)
                .eq(ChatRunEntity::getSessionId, sessionId);
    }

    /** 校验既有 Run 的请求体哈希一致，不一致视为同 ID 不同请求体冲突。 */
    private static void requireSameRequest(ChatRunEntity existing, String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_REQUEST_CONFLICT, existing.getClientRequestId());
        }
    }

    /** 计算请求体规范化哈希：内容 + 排序去重附件，采用 length-prefixed 编码避免拼接歧义。 */
    private static String hashRequest(SendMessage message) {
        List<String> attachments = message.getAttachmentIds() == null
                ? List.of()
                : message.getAttachmentIds().stream().distinct().sorted().toList();
        StringBuilder canonical = new StringBuilder();
        appendHashPart(canonical, StringUtils.defaultString(message.getContent()));
        attachments.forEach(attachment -> appendHashPart(canonical, attachment));
        return sha256(canonical.toString());
    }

    /** 以 {@code 长度:值} 形式追加一段，防止不同分段拼接出相同哈希。 */
    private static void appendHashPart(StringBuilder target, String value) {
        String safeValue = StringUtils.defaultString(value);
        target.append(safeValue.length()).append(':').append(safeValue);
    }

    /** SHA-256 十六进制摘要。 */
    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 生成新的 AGUI 侧 Run 标识。 */
    private static String newAguiRunId() {
        return "agui-" + IdUtil.randomUUID();
    }

    /** 取数据库当前时间，用于心跳与失效判定，避免节点时钟偏差。 */
    private LocalDateTime dbNow() {
        return runMapper.selectDbNow();
    }
}
