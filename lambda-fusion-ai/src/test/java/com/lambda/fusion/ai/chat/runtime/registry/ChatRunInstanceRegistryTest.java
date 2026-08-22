package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class ChatRunInstanceRegistryTest {

    @Test
    void shouldCheckCapacityAndRegisterAtomically() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getChat().getRun().setMaxActiveRuns(1);
        ChatExecutionInstanceFactory instanceFactory = mock(ChatExecutionInstanceFactory.class);
        ChatRunEntity firstRun = run("run-1");
        ChatRunEntity secondRun = run("run-2");
        ChatSessionEntity session = session();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ChatExecutionInstance first = execution(firstRun, session);
        ChatExecutionInstance second = execution(secondRun, session);
        when(instanceFactory.createAgentBacked(firstRun, session, scheduler)).thenReturn(first);
        when(instanceFactory.createAgentBacked(secondRun, session, scheduler)).thenReturn(second);
        ChatExecutionInstanceRegistry registry = new ChatExecutionInstanceRegistry(instanceFactory, properties);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Boolean>> attempts = List.of(
                    attemptRegister(executor, registry, firstRun, session, scheduler, ready, start),
                    attemptRegister(executor, registry, secondRun, session, scheduler, ready, start));
            ready.await();
            start.countDown();

            assertThat(attempts.stream().map(CompletableFuture::join)).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 契约：同标识活动实例已存在时注册必须快速失败，暴露重复启动缺陷。 */
    @Test
    void shouldRejectDuplicateRegistration() {
        AiProperties properties = new AiProperties();
        ChatExecutionInstanceFactory instanceFactory = mock(ChatExecutionInstanceFactory.class);
        ChatRunEntity run = run("run-1");
        ChatSessionEntity session = session();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ChatExecutionInstance execution = execution(run, session);
        when(instanceFactory.createAgentBacked(run, session, scheduler)).thenReturn(execution);
        ChatExecutionInstanceRegistry registry = new ChatExecutionInstanceRegistry(instanceFactory, properties);

        assertThat(registry.registerForStartIfCapacity(run, session, scheduler)).isPresent();
        assertThatThrownBy(() -> registry.registerForStartIfCapacity(run, session, scheduler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run-1");
    }

    private static CompletableFuture<Boolean> attemptRegister(
            ExecutorService executor,
            ChatExecutionInstanceRegistry registry,
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            CountDownLatch ready,
            CountDownLatch start) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ready.countDown();
                    try {
                        start.await();
                        return registry.registerForStartIfCapacity(run, session, scheduler)
                                .isPresent();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                },
                executor);
    }

    private static ChatRunEntity run(String runId) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId(runId);
        return run;
    }

    private static ChatSessionEntity session() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setTenantId("tenant-1");
        session.setUserId("user-1");
        return session;
    }

    private static ChatExecutionInstance execution(ChatRunEntity run, ChatSessionEntity session) {
        ChatExecutionInstance execution = mock(ChatExecutionInstance.class);
        when(execution.run()).thenReturn(run);
        when(execution.session()).thenReturn(session);
        when(execution.drainedSignal()).thenReturn(new CompletableFuture<>());
        return execution;
    }
}
