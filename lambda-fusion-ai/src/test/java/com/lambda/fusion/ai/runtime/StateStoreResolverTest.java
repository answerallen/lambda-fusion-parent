package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证 {@link AgentFactory#resolveStateStore} 按配置选择 MEMORY / FILE，并按 type 分发到
 * {@link StateStoreProvider}（无匹配 provider 或创建失败时回退 MEMORY）。
 *
 * @author Jin
 */
class StateStoreResolverTest {

    @Test
    void defaultsToInMemory() {
        AgentStateStore store = AgentFactory.resolveStateStore(new AiProperties());
        assertThat(store).isInstanceOf(InMemoryAgentStateStore.class);
    }

    @Test
    void fileModeUsesConfiguredRoot(@TempDir Path tempDir) {
        AiProperties props = new AiProperties();
        props.getStateStore().setType("FILE");
        props.getStateStore().setRoot(tempDir.toString());

        AgentStateStore store = AgentFactory.resolveStateStore(props);

        assertThat(store).isInstanceOf(JsonFileAgentStateStore.class);
        assertThat(((JsonFileAgentStateStore) store).getRootDirectory()).isEqualTo(tempDir);
    }

    @Test
    void fileModeFallsBackToWorkspaceRoot(@TempDir Path tempDir) throws Exception {
        Path wsRoot = Files.createDirectories(tempDir.resolve("ws"));
        AiProperties props = new AiProperties();
        props.getStateStore().setType("FILE");
        props.getWorkspace().setRoot(wsRoot.toString());

        AgentStateStore store = AgentFactory.resolveStateStore(props);

        assertThat(store).isInstanceOf(JsonFileAgentStateStore.class);
        assertThat(((JsonFileAgentStateStore) store).getRootDirectory()).isEqualTo(wsRoot.resolve("state"));
    }

    @Test
    void unknownTypeFallsBackToInMemory() {
        AiProperties props = new AiProperties();
        props.getStateStore().setType("UNKNOWN_STORAGE");
        assertThat(AgentFactory.resolveStateStore(props)).isInstanceOf(InMemoryAgentStateStore.class);
    }

    @Test
    void distributedTypeWithoutProviderFallsBackToInMemory() {
        AiProperties props = new AiProperties();
        props.getStateStore().setType("REDIS");
        // 无 provider（扩展未安装）-> 回退 MEMORY
        assertThat(AgentFactory.resolveStateStore(props)).isInstanceOf(InMemoryAgentStateStore.class);
    }

    @Test
    void dispatchesToMatchingProvider() {
        AgentStateStore sentinel = new InMemoryAgentStateStore();
        StateStoreProvider redisProvider = new StateStoreProvider() {
            @Override
            public StateStoreType type() {
                return StateStoreType.REDIS;
            }

            @Override
            public AgentStateStore create() {
                return sentinel;
            }
        };
        AiProperties props = new AiProperties();
        props.getStateStore().setType("REDIS");

        AgentStateStore store = AgentFactory.resolveStateStore(props, List.of(redisProvider));

        assertThat(store).isSameAs(sentinel);
    }

    @Test
    void providerMismatchFallsBackToInMemory() {
        StateStoreProvider redisProvider = new StateStoreProvider() {
            @Override
            public StateStoreType type() {
                return StateStoreType.REDIS;
            }

            @Override
            public AgentStateStore create() {
                return new InMemoryAgentStateStore();
            }
        };
        AiProperties props = new AiProperties();
        props.getStateStore().setType("MYSQL"); // 只有 REDIS provider，不匹配

        AgentStateStore store = AgentFactory.resolveStateStore(props, List.of(redisProvider));

        assertThat(store).isInstanceOf(InMemoryAgentStateStore.class);
    }

    @Test
    void providerFailureFallsBackToInMemory() {
        StateStoreProvider failingProvider = new StateStoreProvider() {
            @Override
            public StateStoreType type() {
                return StateStoreType.REDIS;
            }

            @Override
            public AgentStateStore create() {
                throw new IllegalStateException("boom");
            }
        };
        AiProperties props = new AiProperties();
        props.getStateStore().setType("REDIS");

        AgentStateStore store = AgentFactory.resolveStateStore(props, List.of(failingProvider));

        assertThat(store).isInstanceOf(InMemoryAgentStateStore.class);
    }
}
