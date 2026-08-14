package com.lambda.fusion.ai.chat.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.execution.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEvent;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventSubscription;
import com.lambda.fusion.ai.chat.execution.runtime.ExecutionCoordinator;
import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.RunContext;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.service.ChatRunService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 唯一的对话入口。SSE 只订阅 Run 事件，连接断开不会取消后台 Agent。 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRunService runService;
    private final ExecutionCoordinator executionCoordinator;
    private final AiProperties properties;

    @Override
    public SseEmitter streamChat(String sessionId, SendMessage message) {
        Assert.isTrue(message.isContentOrAttachmentPresent(), "消息内容与附件不能同时为空");
        RunContext context = runService.createOrLoad(sessionId, message);
        executionCoordinator.ensureStarted(context.run(), context.session());
        return attach(context.run(), 0, true);
    }

    @Override
    public Optional<ChatRun> activeRun(String sessionId) {
        return runService.getActiveOwned(sessionId);
    }

    @Override
    public ChatRun getRun(String sessionId, String runId) {
        return runService.getOwned(sessionId, runId);
    }

    @Override
    public SseEmitter resume(String sessionId, String runId, long afterSeq, boolean bootstrap) {
        return attach(runService.loadOwned(sessionId, runId).run(), afterSeq, bootstrap);
    }

    @Override
    public SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command) {
        RunContext context = runService.loadOwned(sessionId, runId);
        ExecutionCoordinator.PreparedConfirmation prepared =
                executionCoordinator.prepareConfirmation(context.run(), context.session(), command);

        ConfirmTransition transition = runService.confirm(sessionId, runId, command);
        if (transition.resumed()) {
            executionCoordinator.resumePrepared(transition.run(), transition.session(), prepared);
        }
        return attach(transition.run(), 0, true);
    }

    @Override
    public void stop(String sessionId, String runId) {
        RunContext context = runService.loadOwned(sessionId, runId);
        executionCoordinator.stop(context.run(), context.session());
    }

    private SseEmitter attach(ChatRunEntity run, long afterSeq, boolean bootstrap) {
        executionCoordinator.validateCursor(run, afterSeq, bootstrap);
        long timeout = properties.getChat().getRun().getConnectionTimeoutSeconds() * 1000;
        SseEmitter emitter = new SseEmitter(timeout);
        AtomicBoolean detached = new AtomicBoolean();
        AtomicReference<ExecutionEventSubscription> subscription = new AtomicReference<>();
        Runnable detach = () -> {
            if (!detached.compareAndSet(false, true)) {
                return;
            }
            ExecutionEventSubscription current = subscription.getAndSet(null);
            if (current != null) {
                current.close();
            }
        };
        emitter.onCompletion(detach);
        emitter.onTimeout(detach);
        emitter.onError(error -> detach.run());

        try {
            long cursor = afterSeq;
            boolean phaseClosed = false;
            if (bootstrap) {
                ExecutionCoordinator.BootstrapBatch batch = executionCoordinator.bootstrap(run);
                for (String event : batch.events()) {
                    emitter.send(SseEmitter.event().data(event));
                }
                cursor = batch.highWatermark();
                phaseClosed = batch.phaseClosed();
            }
            if (phaseClosed
                    || ChatRunStatus.isTerminal(run.getStatus())
                    || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                if (!bootstrap) {
                    ExecutionEventSubscription replay = executionCoordinator.subscribe(
                            run.getId(), cursor, event -> send(emitter, event), emitter::completeWithError);
                    subscription.set(replay);
                    if (detached.get()) {
                        replay.close();
                    } else {
                        replay.whenDrained(emitter::complete);
                    }
                    return emitter;
                }
                emitter.complete();
                return emitter;
            }
            ExecutionEventSubscription attached = executionCoordinator.subscribe(
                    run.getId(), cursor, event -> send(emitter, event), emitter::completeWithError);
            subscription.set(attached);
            if (detached.get()) {
                attached.close();
            }
        } catch (AiBusinessException businessError) {
            detach.run();
            throw businessError;
        } catch (RuntimeException | IOException error) {
            detach.run();
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, ExecutionEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.id()).data(event.data()));
            String type = AguiEventJsonCodec.readEventType(event.data());
            if ("RUN_FINISHED".equals(type) || "RUN_ERROR".equals(type)) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException disconnected) {
            emitter.completeWithError(disconnected);
            throw new SubscriberDisconnectedException(disconnected);
        }
    }

    private static final class SubscriberDisconnectedException extends RuntimeException {
        private SubscriberDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
