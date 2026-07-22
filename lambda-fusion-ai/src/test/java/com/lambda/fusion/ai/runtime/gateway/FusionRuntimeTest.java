package com.lambda.fusion.ai.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.harness.agent.gateway.MsgContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link RuntimeProperty} 能从 Gateway 构建的 {@link io.agentscope.core.agent.RuntimeContext}（挂载 {@code msgContext}）中读出
 * LF 业务上下文（tenantId / appId / lfSessionId）。
 *
 * @author Jin
 */
class FusionRuntimeTest {

    @Test
    void readsContextFromGatewayRuntimeContext() {
        Map<String, String> extra = new HashMap<>();
        extra.put("agentId", "app:A1:t:T1");
        extra.put(RuntimeProperty.KEY_APP_ID, "A1");
        extra.put(RuntimeProperty.KEY_TENANT_ID, "T1");
        extra.put(RuntimeProperty.KEY_LF_SESSION_ID, "sess-1");
        MsgContext msgCtx = new MsgContext("fusion-chat", "T1", "sess-1", null, null, extra, "user-1");

        // 镜像 HarnessGateway.runStream 的行为：把 msgContext 挂到 RuntimeContext
        io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                .sessionId("gw-hash")
                .userId("user-1")
                .put(RuntimeProperty.MSG_CONTEXT_KEY, msgCtx)
                .build();

        assertThat(RuntimeProperty.msgContext(ctx)).isSameAs(msgCtx);
        assertThat(RuntimeProperty.appId(ctx)).isEqualTo("A1");
        assertThat(RuntimeProperty.tenantId(ctx)).isEqualTo("T1");
        assertThat(RuntimeProperty.lfSessionId(ctx)).isEqualTo("sess-1");
    }

    @Test
    void returnsNullWhenNoMsgContext() {
        io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                .sessionId("gw-hash")
                .build();
        assertThat(RuntimeProperty.msgContext(ctx)).isNull();
        assertThat(RuntimeProperty.tenantId(ctx)).isNull();
        assertThat(RuntimeProperty.appId(ctx)).isNull();
        assertThat(RuntimeProperty.lfSessionId(ctx)).isNull();
    }

    @Test
    void returnsNullForMissingExtra() {
        MsgContext msgCtx = new MsgContext("fusion-chat", null, "sess-1", null, null, Map.of(), "user-1");
        io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                .put(RuntimeProperty.MSG_CONTEXT_KEY, msgCtx)
                .build();
        // tenantId 未透传（无租户会话）-> null，不应抛 NPE
        assertThat(RuntimeProperty.tenantId(ctx)).isNull();
        assertThat(RuntimeProperty.lfSessionId(ctx)).isNull();
    }
}
