package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
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
        ChatRunInstanceFactory instanceFactory = mock(ChatRunInstanceFactory.class);
        ChatRunEntity firstRun = run("run-1");
        ChatRunEntity secondRun = run("run-2");
        ChatSessionEntity session = session();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ChatRunInstance first = execution(firstRun, session);
        ChatRunInstance second = execution(secondRun, session);
        when(instanceFactory.createExecution(firstRun, session, scheduler)).thenReturn(first);
        when(instanceFactory.createExecution(secondRun, session, scheduler)).thenReturn(second);
        ChatRunInstanceRegistry registry = new ChatRunInstanceRegistry(instanceFactory, properties);
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

    private static CompletableFuture<Boolean> attemptRegister(
            ExecutorService executor,
            ChatRunInstanceRegistry registry,
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
                                .map(ChatRunInstanceRegistry.StartRegistration::registered)
                                .orElse(false);
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

    private static ChatRunInstance execution(ChatRunEntity run, ChatSessionEntity session) {
        ChatRunInstance execution = mock(ChatRunInstance.class);
        when(execution.run()).thenReturn(run);
        when(execution.session()).thenReturn(session);
        when(execution.drainedSignal()).thenReturn(new CompletableFuture<>());
        return execution;
    }
}
