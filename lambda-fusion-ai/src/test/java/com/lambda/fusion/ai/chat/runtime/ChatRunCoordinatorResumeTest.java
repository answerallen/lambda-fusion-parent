package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapModel;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatRunCoordinatorResumeTest {

    private ChatExecutionService coordinator;

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void shouldReturnPersistedSnapshotAndCloseWhenRealtimeBufferIsNotLocal() {
        ChatRunStateService runService = mock(ChatRunStateService.class);
        ChatRunEventStore eventStore = mock(ChatRunEventStore.class);
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setAguiRunId("agui-1");
        run.setPhaseNo(1);
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setSnapshotJson(ChatRunSnapshotCodec.encode(new ChatRunSnapshot(
                "run-1", "agui-1", 1, "partial answer", "", "message-1", null, true, false, null, null)));
        when(runService.loadCurrent("run-1")).thenReturn(run);
        when(eventStore.latestCursor("run-1")).thenReturn(0L);
        when(eventStore.contains("run-1")).thenReturn(false);
        coordinator = new ChatExecutionService(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                mock(ChatExecutionInstanceFactory.class),
                new AiProperties());

        AguiBootstrapModel bootstrap = coordinator.bootstrap(run);

        assertThat(bootstrap.phaseClosed()).isTrue();
        assertThat(bootstrap.cursor()).isZero();
        assertThat(bootstrap.events()).anyMatch(event -> event.contains("partial answer"));
    }
}
