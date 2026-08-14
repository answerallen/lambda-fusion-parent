package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.mapper.ChatRunMapper;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.run.RunSnapshot;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.service.AbstractCrudService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Run 的唯一持久化 Service：负责创建幂等、状态迁移、检查点与最终落库。
 *
 * <p>Run 是一次对话回合的执行载体，状态机为：
 * {@code CREATED -> RUNNING <-> AWAITING_CONFIRM -> STOPPING -> COMPLETED/STOPPED/FAILED}。
 *
 * <p>并发与一致性约定：
 *
 * <ul>
 *   <li>创建幂等：以 {@code clientRequestId} 去重，并以 {@code requestHash} 校验同请求体，命中既有 Run 直接复用；
 *   <li>状态迁移用「带前置条件的 UPDATE」（CAS）实现，{@code changed == 1} 才视为迁移成功，避免覆盖并发改动；
 *   <li>检查点与各迁移方法均用 {@link Propagation#REQUIRES_NEW}，使每一次状态推进/快照独立提交，
 *       不被驱动事务的后续失败回滚——这是 Run 进度可恢复的关键。
 * </ul>
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatRunServiceImpl extends AbstractCrudService<ChatRunEntity, ChatRun, ChatRunMapper>
        implements ChatRunService {

    private final ChatRunMapper runMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final AppService appService;

    /** 创建或载入幂等 Run：同一 {@code clientRequestId} 复用既有 Run，否则在无活跃 Run 时新建并落库用户消息。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RunContext createOrLoad(String sessionId, SendMessage message) {
        ChatSessionEntity session = sessionService.loadOwnedForUpdate(sessionId);
        String requestHash = hashRequest(message);
        // 幂等去重：同 clientRequestId 命中既有 Run 时直接复用，下方再校验请求体一致。
        ChatRunEntity existing = findByRequest(sessionId, message.getClientRequestId());
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            return new RunContext(existing, session);
        }
        appService.loadAvailable(session.getAppId());
        // 同会话不允许并发活跃 Run，避免执行上下文竞争。
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
        run.setAguiRunId(newAGuiRunId());
        run.setSnapshotSeq(0L);
        runMapper.insert(run);

        // 落库用户消息并绑定附件，随后回填 userMessageId 与初始空快照。
        ChatMessageEntity userMessage =
                messageService.saveUserMessage(session, StringUtils.defaultString(message.getContent()));
        if (message.getAttachmentIds() != null) {
            attachmentService.bindToMessage(
                    session, message.getAttachmentIds().stream().distinct().toList(), userMessage.getId());
        }
        run.setUserMessageId(userMessage.getId());
        run.setSnapshotJson(
                JsonUtils.getJsonCodec().toJson(RunSnapshot.empty(run.getId(), run.getAguiRunId(), run.getPhaseNo())));
        runMapper.updateById(run);
        // 刷新会话最近消息时间，用于会话列表排序。
        sessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getId, sessionId)
                        .set(ChatSessionEntity::getLastMessageAt, now)
                        .set(ChatSessionEntity::getUpdatedAt, now));
        // 回读一次，拿到 insert + update 后的权威落库状态。
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
     * 确认待确认态的工具调用并推进到下一阶段。
     *
     * <p>以 {@code phaseNo} 做幂等守卫：Run 已越过本次确认阶段则视为重复确认，返回 {@code resumed=false}；
     * 命中当前阶段则以 (status=AWAITING_CONFIRM, phaseNo) 为前置条件 CAS 迁移到 RUNNING 并进入下一阶段。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfirmTransition confirm(String sessionId, String runId, ConfirmToolCall command) {
        ChatSessionEntity session = sessionService.loadOwnedForUpdate(sessionId);
        ChatRunEntity run = runMapper.selectOne(ownedQuery(sessionId, runId).last("FOR UPDATE"));
        if (run == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_NOT_FOUND, runId);
        }
        // 阶段号守卫：Run 已推进到更新阶段(phaseNo 更大)说明本次确认已被处理过，幂等返回未恢复；落后则命令过期。
        if (!Objects.equals(run.getPhaseNo(), command.getPhaseNo())) {
            if (run.getPhaseNo() > command.getPhaseNo()) {
                return new ConfirmTransition(run, session, false);
            }
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }
        // 只有待确认态可被确认，否则状态冲突。
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }
        // 校验决策：每个待确认工具必须被决定恰好一次，不得多不得少。
        Set<String> pendingIds = RunSnapshot.fromJson(run.getSnapshotJson()).pendingTools().stream()
                .map(RunSnapshot.Tool::toolCallId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> decidedIds = new HashSet<>();
        boolean valid = !pendingIds.isEmpty()
                && command.getDecisions().stream()
                        .allMatch(decision -> pendingIds.contains(decision.getToolCallId())
                                && decidedIds.add(decision.getToolCallId()))
                && decidedIds.equals(pendingIds);
        if (!valid) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "确认决策必须完整且不能重复");
        }

        int targetPhase = run.getPhaseNo() + 1;
        String targetAGuiRunId = newAGuiRunId();
        // CAS 推进：以 (status=AWAITING_CONFIRM, phaseNo) 为前置条件迁移到 RUNNING 并进入下一阶段；竞争失败则状态冲突。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .eq(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                        .eq(ChatRunEntity::getPhaseNo, command.getPhaseNo())
                        .set(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                        .set(ChatRunEntity::getPhaseNo, targetPhase)
                        .set(ChatRunEntity::getAguiRunId, targetAGuiRunId)
                        .set(ChatRunEntity::getAwaitConfirmDeadlineAt, null)
                        .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getId());
        }
        // 用迁移后的值刷新内存对象，供调用方继续执行。
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setPhaseNo(targetPhase);
        run.setAguiRunId(targetAGuiRunId);
        return new ConfirmTransition(run, session, true);
    }

    /** CAS 认领新建 Run：{@code CREATED -> RUNNING}，成功返回 true；已被并发认领则返回 false。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claimCreated(ChatRunEntity run) {
        LocalDateTime now = LocalDateTime.now();
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getStatus, ChatRunStatus.CREATED.name())
                                .set(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                                .set(ChatRunEntity::getStartedAt, now)
                                .set(ChatRunEntity::getUpdatedAt, now))
                == 1;
    }

    /** CAS 写检查点：在非终态下更新快照与序号，成功返回 true；已进入终态则拒绝写入。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean checkpoint(ChatRunEntity run, RunSnapshot snapshot, long seq) {
        LocalDateTime now = LocalDateTime.now();
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .notIn(
                                        ChatRunEntity::getStatus,
                                        ChatRunStatus.COMPLETED.name(),
                                        ChatRunStatus.STOPPED.name(),
                                        ChatRunStatus.FAILED.name())
                                .set(
                                        ChatRunEntity::getSnapshotJson,
                                        JsonUtils.getJsonCodec().toJson(snapshot))
                                .set(ChatRunEntity::getSnapshotSeq, seq)
                                .set(ChatRunEntity::getUpdatedAt, now))
                == 1;
    }

    /** CAS 进入待确认：{@code RUNNING -> AWAITING_CONFIRM} 并记录确认截止时间，成功返回 true。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean awaitConfirm(ChatRunEntity run, RunSnapshot snapshot, long seq, LocalDateTime deadline) {
        return runMapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatRunEntity>()
                                .eq(ChatRunEntity::getId, run.getId())
                                .eq(ChatRunEntity::getStatus, ChatRunStatus.RUNNING.name())
                                .set(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                                .set(ChatRunEntity::getAwaitConfirmDeadlineAt, deadline)
                                .set(
                                        ChatRunEntity::getSnapshotJson,
                                        JsonUtils.getJsonCodec().toJson(snapshot))
                                .set(ChatRunEntity::getSnapshotSeq, seq)
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
     * 终结 Run：在非终态下写入终态、助手消息与最终快照，独立事务提交。
     *
     * <p>幂等：已终态时返回 {@code committed=false} 并携带既有终态信息。stop 优先：处于 {@code STOPPING}
     * 时无论目标态为何都落为 {@code STOPPED}（理由 {@code USER_STOP}）并清空错误信息。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FinalizeResult finalizeRun(
            ChatRunEntity identity,
            ChatRunStatus targetStatus,
            String finishReason,
            RunSnapshot snapshot,
            String toolCallJson,
            long lastSeq,
            String errorCode,
            String errorMessage) {
        // 悲观锁会话与 Run，串行化终结，避免与并发迁移/停止竞争。
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
            return new FinalizeResult(
                    false,
                    run.getAssistantMessageId(),
                    run.getStatus(),
                    run.getFinishReason(),
                    run.getErrorCode(),
                    run.getErrorMessage());
        }

        // stop 优先：处于 STOPPING 时无论目标态为何都落为 STOPPED，并清空错误信息。
        boolean stopWon =
                ChatRunStatus.STOPPING.name().equals(run.getStatus()) && targetStatus != ChatRunStatus.STOPPED;
        ChatRunStatus finalStatus = stopWon ? ChatRunStatus.STOPPED : targetStatus;
        String finalReason = stopWon ? "USER_STOP" : finishReason;
        String finalErrorCode = finalStatus == ChatRunStatus.STOPPED ? null : errorCode;
        String finalErrorMessage = finalStatus == ChatRunStatus.STOPPED ? null : errorMessage;

        // 仅 COMPLETED 或仍有正文/工具调用时才落库助手消息。
        ChatMessageEntity assistant = null;
        if (finalStatus == ChatRunStatus.COMPLETED || !snapshot.text().isBlank() || toolCallJson != null) {
            assistant = messageService.saveAssistantMessage(session, snapshot.text(), toolCallJson);
        }
        LocalDateTime now = LocalDateTime.now();
        // CAS 终结：以非终态为前置条件写入终态；竞争失败说明已被并发终结。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .notIn(
                                ChatRunEntity::getStatus,
                                ChatRunStatus.COMPLETED.name(),
                                ChatRunStatus.STOPPED.name(),
                                ChatRunStatus.FAILED.name())
                        .set(ChatRunEntity::getStatus, finalStatus.name())
                        .set(ChatRunEntity::getFinishReason, finalReason)
                        .set(ChatRunEntity::getAssistantMessageId, assistant == null ? null : assistant.getId())
                        .set(ChatRunEntity::getAwaitConfirmDeadlineAt, null)
                        .set(
                                ChatRunEntity::getSnapshotJson,
                                JsonUtils.getJsonCodec().toJson(snapshot))
                        .set(ChatRunEntity::getSnapshotSeq, lastSeq)
                        .set(ChatRunEntity::getErrorCode, finalErrorCode)
                        .set(ChatRunEntity::getErrorMessage, finalErrorMessage)
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
        return new FinalizeResult(
                true,
                assistant == null ? null : assistant.getId(),
                finalStatus.name(),
                finalReason,
                finalErrorCode,
                finalErrorMessage);
    }

    /** 在终态下补写最终快照与序号；行已被清理时容忍不抛，行仍在却写失败则异常。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordTerminalSeq(ChatRunEntity run, RunSnapshot snapshot, long seq) {
        // 仅终态可写最终游标；CAS 以终态为前置条件。
        int changed = runMapper.update(
                null,
                new LambdaUpdateWrapper<ChatRunEntity>()
                        .eq(ChatRunEntity::getId, run.getId())
                        .in(
                                ChatRunEntity::getStatus,
                                ChatRunStatus.COMPLETED.name(),
                                ChatRunStatus.STOPPED.name(),
                                ChatRunStatus.FAILED.name())
                        .set(
                                ChatRunEntity::getSnapshotJson,
                                JsonUtils.getJsonCodec().toJson(snapshot))
                        .set(ChatRunEntity::getSnapshotSeq, seq)
                        .set(ChatRunEntity::getUpdatedAt, LocalDateTime.now()));
        // 写失败但行仍在则异常；行已被清理则容忍（不抛）。
        if (changed != 1 && runMapper.selectById(run.getId()) != null) {
            throw new IllegalStateException("Run终态游标写入失败: " + run.getId());
        }
    }

    /** 按 runId 载入 Run 实体（不做归属校验，供执行器内部使用）。 */
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

    /** 枚举处于 {@code CREATED} 待认领的 Run（调度器扫描用）。 */
    @Override
    public List<ChatRunEntity> listCreated() {
        return runMapper.selectList(
                new LambdaQueryWrapper<ChatRunEntity>().eq(ChatRunEntity::getStatus, ChatRunStatus.CREATED.name()));
    }

    /** 枚举重启时需恢复或终结的中断态 Run（{@code RUNNING/STOPPING/AWAITING_CONFIRM}）。 */
    @Override
    public List<ChatRunEntity> listInterruptedOnRestart() {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .in(
                        ChatRunEntity::getStatus,
                        ChatRunStatus.RUNNING.name(),
                        ChatRunStatus.STOPPING.name(),
                        ChatRunStatus.AWAITING_CONFIRM.name()));
    }

    /** 枚举超时未确认的 Run（{@code AWAITING_CONFIRM} 且截止时间不晚于给定时刻）。 */
    @Override
    public List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline) {
        return runMapper.selectList(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getStatus, ChatRunStatus.AWAITING_CONFIRM.name())
                .le(ChatRunEntity::getAwaitConfirmDeadlineAt, deadline));
    }

    /** 实体转视图，并在待确认态填充待确认工具列表。 */
    private ChatRun toRunView(ChatRunEntity entity) {
        ChatRun view = toVO(entity);
        RunSnapshot snapshot = RunSnapshot.fromJson(entity.getSnapshotJson());
        view.setPendingConfirm(
                ChatRunStatus.AWAITING_CONFIRM.name().equals(entity.getStatus())
                        ? snapshot.pendingTools().stream()
                                .map(tool -> new ChatRun.PendingTool(tool.toolCallId(), tool.toolCallName()))
                                .toList()
                        : List.of());
        return view;
    }

    /** 按 (sessionId, clientRequestId) 查找既有 Run（幂等去重用）。 */
    private ChatRunEntity findByRequest(String sessionId, String requestId) {
        return runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getSessionId, sessionId)
                .eq(ChatRunEntity::getClientRequestId, requestId));
    }

    /** 查找会话内非终态的活跃 Run。 */
    private ChatRunEntity findActive(String sessionId) {
        return runMapper.selectOne(new LambdaQueryWrapper<ChatRunEntity>()
                .eq(ChatRunEntity::getSessionId, sessionId)
                .notIn(
                        ChatRunEntity::getStatus,
                        ChatRunStatus.COMPLETED.name(),
                        ChatRunStatus.STOPPED.name(),
                        ChatRunStatus.FAILED.name()));
    }

    /** 构造 (runId, sessionId) 双键查询，确保 Run 归属目标会话。 */
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
    private static String newAGuiRunId() {
        return "agui-" + IdUtil.randomUUID();
    }
}
