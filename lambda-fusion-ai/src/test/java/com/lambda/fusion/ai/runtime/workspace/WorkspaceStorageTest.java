package com.lambda.fusion.ai.runtime.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiConstants.WorkspaceStorageType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.sandbox.SandboxExecutionGuard;
import io.agentscope.harness.agent.sandbox.SandboxLease;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepLegacyLocalDirectoryAndFileBehavior() throws Exception {
        AiProperties properties = properties("LOCAL");
        WorkspaceStorage storage = storage(properties, List.of());
        storage.initialize();
        WorkspaceFileService files = new WorkspaceFileService(storage);
        AppEntity app = app();

        files.write("tenant-a", app, "memory/note.md", "local-note");

        assertThat(storage.type()).isEqualTo(WorkspaceStorageType.LOCAL);
        assertThat(files.read("tenant-a", app, "memory/note.md")).isEqualTo("local-note");
        assertThat(files.list("tenant-a", app))
                .extracting(WorkspaceFileEntry::path)
                .contains("AGENTS.md", "memory", "memory/note.md", "tools.json");
        assertThat(tempDir.resolve("tenants/tenant-a/apps/app-1/memory/note.md"))
                .hasContent("local-note");
        assertThat(tempDir.resolve(".remote-templates")).doesNotExist();
    }

    @Test
    void shouldShareRemoteWorkspaceAcrossInstancesAndKeepTenantIsolation() throws Exception {
        BaseStore sharedBaseStore = new InMemoryStore();
        WorkspaceStorage firstStorage =
                storage(properties("MYSQL"), List.of(provider(distributedStore(sharedBaseStore))));
        WorkspaceStorage secondStorage =
                storage(properties("MYSQL"), List.of(provider(distributedStore(sharedBaseStore))));
        firstStorage.initialize();
        secondStorage.initialize();
        WorkspaceFileService firstNode = new WorkspaceFileService(firstStorage);
        WorkspaceFileService secondNode = new WorkspaceFileService(secondStorage);
        AppEntity app = app();

        firstNode.write("tenant-a", app, "AGENTS.md", "shared-agent");
        firstNode.write("tenant-a", app, "memory/note.md", "shared-note");

        assertThat(secondNode.read("tenant-a", app, "AGENTS.md")).isEqualTo("shared-agent");
        assertThat(secondNode.read("tenant-a", app, "memory/note.md")).isEqualTo("shared-note");
        assertThat(secondNode.read("tenant-b", app, "AGENTS.md")).contains("Demo Agent");
        List<WorkspaceFileEntry> entries = secondNode.list("tenant-a", app);
        assertThat(entries)
                .allSatisfy(entry -> assertThat(entry.path()).doesNotContain(".."))
                .filteredOn(entry -> entry.path().equals("AGENTS.md"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.size()).isEqualTo("shared-agent".getBytes().length);
                    assertThat(entry.updatedAt()).isPositive();
                });
        assertThat(entries)
                .extracting(WorkspaceFileEntry::path)
                .contains("memory/note.md")
                .allSatisfy(path -> assertThat(path.split("/")[0])
                        .isIn(
                                ".audit",
                                "AGENTS.md",
                                "MEMORY.md",
                                "agents",
                                "knowledge",
                                "memory",
                                "plans",
                                "skills",
                                "subagents",
                                "tools.json"));
        assertThatThrownBy(() -> firstNode.write("tenant-a", app, ".agentscope/bus/events", "invalid"))
                .isInstanceOf(AiBusinessException.class);
        assertThatThrownBy(() -> firstNode.write("tenant-a", app, "./.index/workspace.db", "invalid"))
                .isInstanceOf(AiBusinessException.class);

        AbstractFilesystem firstFilesystem = firstStorage.openDistributedFilesystem(
                "tenant-a", app, firstStorage.initializeWorkspace("tenant-a", app));
        firstFilesystem.uploadFiles(
                RuntimeContext.empty(),
                List.of(Map.entry(".agentscope/bus/event.json", "internal-event".getBytes(StandardCharsets.UTF_8))));
        AbstractFilesystem secondFilesystem = secondStorage.openDistributedFilesystem(
                "tenant-a", app, secondStorage.initializeWorkspace("tenant-a", app));
        assertThat(secondFilesystem
                        .read(RuntimeContext.empty(), ".agentscope/bus/event.json", 0, 0)
                        .fileData()
                        .content())
                .isEqualTo("internal-event");

        Path remoteTemplate = tempDir.resolve(".remote-templates/mysql/tenants/tenant-a/apps/app-1/AGENTS.md");
        assertThat(remoteTemplate).exists();
        assertThat(Files.readString(remoteTemplate)).contains("Demo Agent");
        assertThat(tempDir.resolve("tenants/tenant-a/apps/app-1")).doesNotExist();
    }

    @Test
    void shouldRejectRemoteWorkspaceWithLocalAgentStateStore() {
        AiProperties properties = properties("MYSQL");
        properties.getStateStore().setType("MEMORY");
        WorkspaceStorage storage = storage(properties, List.of(provider(distributedStore())));

        assertThatThrownBy(storage::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("远程 Workspace 要求分布式 Agent 状态存储");
    }

    @Test
    void shouldNotAcquireDistributedLockAgainForAuditWrite() throws Exception {
        AtomicInteger lockAcquisitions = new AtomicInteger();
        SandboxExecutionGuard guard = key -> {
            lockAcquisitions.incrementAndGet();
            return SandboxLease.noop();
        };
        DistributedStore store = DistributedStore.builder()
                .agentStateStore(new InMemoryAgentStateStore())
                .baseStore(new InMemoryStore())
                .sandboxExecutionGuard(guard)
                .build();
        WorkspaceStorage storage = storage(properties("MYSQL"), List.of(provider(store)));
        storage.initialize();
        WorkspaceFileService files = new WorkspaceFileService(storage);
        AppEntity app = app();

        files.write("tenant-a", app, "memory/note.md", "managed-write");
        files.writeWhileAgentLocked("tenant-a", app, ".audit/1/memory/note.md", "audit-write");

        assertThat(lockAcquisitions).hasValue(1);
        assertThat(files.read("tenant-a", app, ".audit/1/memory/note.md")).isEqualTo("audit-write");
    }

    @Test
    void shouldRejectUnknownWorkspaceStorageType() {
        WorkspaceStorage storage = storage(properties("UNKNOWN"), List.of());

        assertThatThrownBy(storage::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知的 Workspace 存储类型");
    }

    private AiProperties properties(String storageType) {
        AiProperties properties = new AiProperties();
        properties.getWorkspace().setRoot(tempDir.toString());
        properties.getWorkspace().getStorage().setType(storageType);
        properties.getStateStore().setType("MYSQL");
        return properties;
    }

    private WorkspaceStorage storage(AiProperties properties, List<WorkspaceDistributedStoreProvider> providers) {
        return new WorkspaceStorage(properties, new WorkspacePaths(properties), new WorkspaceScaffolder(), providers);
    }

    private WorkspaceDistributedStoreProvider provider(DistributedStore distributedStore) {
        return new WorkspaceDistributedStoreProvider() {
            @Override
            public WorkspaceStorageType type() {
                return WorkspaceStorageType.MYSQL;
            }

            @Override
            public DistributedStore create() {
                return distributedStore;
            }
        };
    }

    private DistributedStore distributedStore() {
        return distributedStore(new InMemoryStore());
    }

    private DistributedStore distributedStore(BaseStore baseStore) {
        return DistributedStore.builder()
                .agentStateStore(new InMemoryAgentStateStore())
                .baseStore(baseStore)
                .build();
    }

    private AppEntity app() {
        AppEntity app = new AppEntity();
        app.setId("app-1");
        app.setName("Demo Agent");
        app.setAppType("WORKSPACE");
        app.setSelfEvolve(Boolean.TRUE);
        return app;
    }
}
