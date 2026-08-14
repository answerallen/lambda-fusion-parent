package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
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
 * {@link StateStoreProvider}。显式配置非 MEMORY/FILE 类型时，扩展缺失或创建失败必须启动失败，
 * 不再静默回退 MEMORY。
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
    void unknownTypeFailsFast() {
        AiProperties props = new AiProperties();
        props.getStateStore().setType("UNKNOWN_STORAGE");
        assertThatThrownBy(() -> AgentFactory.resolveStateStore(props))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CONFIGURATION_ERROR.getCode()));
    }

    @Test
    void distributedTypeWithoutProviderFailsFast() {
        AiProperties props = new AiProperties();
        props.getStateStore().setType("REDIS");
        assertThatThrownBy(() -> AgentFactory.resolveStateStore(props))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CONFIGURATION_ERROR.getCode()));
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
    void providerMismatchFailsFast() {
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

        assertThatThrownBy(() -> AgentFactory.resolveStateStore(props, List.of(redisProvider)))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CONFIGURATION_ERROR.getCode()));
    }

    @Test
    void providerFailureFailsFast() {
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

        assertThatThrownBy(() -> AgentFactory.resolveStateStore(props, List.of(failingProvider)))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CONFIGURATION_ERROR.getCode()));
    }
}
