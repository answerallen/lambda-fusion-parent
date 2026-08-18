package com.lambda.fusion.ai.runtime.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import com.lambda.fusion.authority.api.RemoteUser;
import com.lambda.fusion.authority.api.RemoteUserService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 当前用户信息查询工具测试：固定「身份来自 RuntimeContext 注入、不进模型可见 schema」，
 * 以及「authority 远程详情优先、缺失/异常时降级为基础身份」。
 *
 * @author zx
 */
class CurrentUserQueryToolTest {

    /** 无远程服务：authority 不在 classpath / 未装配时的降级路径。 */
    private final CurrentUserQueryTool toolWithoutRemote = new CurrentUserQueryTool(emptyProvider());

    @Test
    void shouldResolveCurrentUserFromInjectedRuntimeContext() {
        String result = toolWithoutRemote.queryCurrentUser(context());

        assertThat(result)
                .contains("zhangsan")
                .contains("tenant-1")
                .contains("session-1")
                .contains("app-1");
    }

    @Test
    void shouldHandleMissingContextGracefully() {
        assertThat(toolWithoutRemote.queryCurrentUser(null)).contains("无法确定当前用户");
    }

    @Test
    void shouldRenderRemoteUserDetailsWhenAvailable() {
        RemoteUser detail = new RemoteUser();
        detail.setUsername("zhangsan");
        detail.setNickname("张三");
        detail.setTenantId("tenant-1");
        detail.setOrgName("研发部");
        detail.setOrgFullName("总部/研发部");
        detail.setRoles(List.of("ROLE_ADMIN", "ROLE_USER"));
        detail.setEnabled(true);
        detail.setLocked(false);
        detail.setMobile("13800000000");
        detail.setEmail("zhangsan@example.com");
        CurrentUserQueryTool tool = new CurrentUserQueryTool(providerOf(username -> detail));

        String result = tool.queryCurrentUser(context());

        assertThat(result)
                .contains("zhangsan")
                .contains("张三")
                .contains("总部/研发部")
                .contains("ROLE_ADMIN")
                .contains("正常")
                .contains("13800000000")
                .contains("zhangsan@example.com")
                .contains("session-1")
                .contains("app-1");
    }

    @Test
    void shouldDegradeToBasicIdentityWhenRemoteUserMissing() {
        CurrentUserQueryTool tool = new CurrentUserQueryTool(providerOf(username -> null));

        String result = tool.queryCurrentUser(context());

        assertThat(result)
                .contains("zhangsan")
                .contains("tenant-1")
                .contains("session-1")
                .contains("app-1");
    }

    @Test
    void shouldDegradeToBasicIdentityWhenRemoteCallFails() {
        CurrentUserQueryTool tool = new CurrentUserQueryTool(providerOf(username -> {
            throw new IllegalStateException("dubbo unavailable");
        }));

        String result = tool.queryCurrentUser(context());

        assertThat(result)
                .contains("zhangsan")
                .contains("tenant-1")
                .contains("session-1")
                .contains("app-1");
    }

    @Test
    void shouldNotExposeRuntimeContextInToolSchema() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(toolWithoutRemote);

        List<ToolSchema> schemas = toolkit.getToolSchemas();
        assertThat(schemas).hasSize(1);
        ToolSchema schema = schemas.get(0);
        assertThat(schema.getName()).isEqualTo(CurrentUserQueryTool.TOOL_NAME);
        // RuntimeContext 参数由框架注入、对模型不可见：schema 不应包含任何 LLM 可见参数。
        String schemaJson = schema.toString();
        assertThat(schemaJson)
                .doesNotContain("RuntimeContext")
                .doesNotContain("userId")
                .doesNotContain("tenantId");
    }

    private static RuntimeContext context() {
        return RuntimeContext.builder()
                .userId("zhangsan")
                .sessionId("session-1")
                .put(RuntimeProperty.KEY_TENANT_ID, "tenant-1")
                .put(RuntimeProperty.KEY_APP_ID, "app-1")
                .put(RuntimeProperty.KEY_LF_SESSION_ID, "session-1")
                .build();
    }

    private static ObjectProvider<RemoteUserService> emptyProvider() {
        return providerOf(null);
    }

    /** 用 Lambda 伪造 {@link RemoteUserService}，包一层最小 {@link ObjectProvider}（无需 Mockito）。 */
    private static ObjectProvider<RemoteUserService> providerOf(RemoteUserService service) {
        return new ObjectProvider<>() {
            @Override
            public RemoteUserService getObject() {
                return require(service);
            }

            @Override
            public RemoteUserService getObject(Object... args) {
                return require(service);
            }

            @Override
            public RemoteUserService getIfAvailable() {
                return service;
            }

            @Override
            public RemoteUserService getIfUnique() {
                return service;
            }

            private RemoteUserService require(RemoteUserService s) {
                if (s == null) {
                    throw new IllegalStateException("no RemoteUserService bean");
                }
                return s;
            }
        };
    }
}
