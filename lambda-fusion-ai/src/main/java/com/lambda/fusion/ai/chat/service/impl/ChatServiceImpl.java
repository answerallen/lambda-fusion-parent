package com.lambda.fusion.ai.chat.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.RunContext;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.ChatRunCoordinator;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
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

    private final ChatRunService chatRunService;
    private final ChatRunCoordinator chatRunCoordinator;
    private final AiProperties properties;

    @Override
    public SseEmitter streamChat(String sessionId, SendMessage message) {
        Assert.isTrue(message.isContentOrAttachmentPresent(), "消息内容与附件不能同时为空");
        RunContext context = chatRunService.createOrLoad(sessionId, message);
        chatRunCoordinator.startIfCreated(context.run(), context.session());
        return openRunEventStream(context.run(), true);
    }

    @Override
    public Optional<ChatRun> activeRun(String sessionId) {
        return chatRunService.getActiveOwned(sessionId);
    }

    @Override
    public ChatRun getRun(String sessionId, String runId) {
        return chatRunService.getOwned(sessionId, runId);
    }

    @Override
    public SseEmitter resume(String sessionId, String runId, boolean bootstrap) {
        return openRunEventStream(chatRunService.loadOwned(sessionId, runId).run(), bootstrap);
    }

    @Override
    public SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command) {
        RunContext context = chatRunService.loadOwned(sessionId, runId);
        ConfirmTransition transition = chatRunCoordinator.confirm(context.run(), context.session(), command);
        return openRunEventStream(transition.run(), true);
    }

    @Override
    public void stop(String sessionId, String runId) {
        RunContext context = chatRunService.loadOwned(sessionId, runId);
        chatRunCoordinator.stop(context.run(), context.session());
    }

    private SseEmitter openRunEventStream(ChatRunEntity chatRunEntity, boolean bootstrap) {
        long timeout = properties.getChat().getRun().getConnectionTimeoutSeconds() * 1000;
        SseEmitter emitter = new SseEmitter(timeout);
        AtomicBoolean detached = new AtomicBoolean();
        AtomicReference<ChatRunEventSubscription> subscription = new AtomicReference<>();
        Runnable runnable = () -> {
            if (!detached.compareAndSet(false, true)) {
                return;
            }
            ChatRunEventSubscription current = subscription.getAndSet(null);
            if (current != null) {
                current.close();
            }
        };
        emitter.onCompletion(runnable);
        emitter.onTimeout(runnable);
        emitter.onError(error -> runnable.run());

        try {
            long cursor = 0;
            boolean phaseClosed = false;
            if (bootstrap) {
                AguiBootstrap aguiBootstrap = chatRunCoordinator.bootstrap(chatRunEntity);
                for (String event : aguiBootstrap.events()) {
                    emitter.send(SseEmitter.event().data(event));
                }
                cursor = aguiBootstrap.highWatermark();
                phaseClosed = aguiBootstrap.phaseClosed();
            }
            if (phaseClosed
                    || ChatRunStatus.isTerminal(chatRunEntity.getStatus())
                    || ChatRunStatus.AWAITING_CONFIRM.name().equals(chatRunEntity.getStatus())) {
                if (!bootstrap) {
                    ChatRunEventSubscription replay = chatRunCoordinator.subscribe(
                            chatRunEntity.getId(), cursor, event -> send(emitter, event), emitter::completeWithError);
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
            ChatRunEventSubscription attached = chatRunCoordinator.subscribe(
                    chatRunEntity.getId(), cursor, event -> send(emitter, event), emitter::completeWithError);
            subscription.set(attached);
            if (detached.get()) {
                attached.close();
            }
        } catch (AiBusinessException businessError) {
            runnable.run();
            throw businessError;
        } catch (RuntimeException | IOException error) {
            runnable.run();
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, ChatRunEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.id()).data(event.data()));
            if ("RUN_FINISHED".equals(event.type()) || "RUN_ERROR".equals(event.type())) {
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
