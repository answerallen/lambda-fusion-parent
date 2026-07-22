package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import com.lambda.fusion.ai.mcp.service.McpServerService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 工具集装配器：为每个应用构建 {@link Toolkit}。
 *
 * <p>装配内容：
 * <ul>
 *   <li>容器中所有带 {@code @Tool} 方法的 Spring Bean（全局本地工具）</li>
 *   <li>应用绑定的 MCP 服务（每个独立装载，单个失败不影响其余）</li>
 * </ul>
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ToolkitAssembler {

    private final ApplicationContext applicationContext;
    private final McpServerService mcpServerService;

    private List<Object> localToolBeans = Collections.emptyList();

    @PostConstruct
    public void init() {
        // 全局扫描一次：容器中所有带 @Tool 方法的 Bean 注册为 Agent 共享工具
        List<Object> tools = new ArrayList<>();
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name);
            if (type != null && hasToolMethod(type)) {
                tools.add(applicationContext.getBean(name));
            }
        }
        this.localToolBeans = Collections.unmodifiableList(tools);
        log.info("扫描到 {} 个本地 @Tool Bean", tools.size());
    }

    public Toolkit build(AppEntity app) {
        Toolkit toolkit = new Toolkit();
        for (Object toolBean : localToolBeans) {
            toolkit.registerTool(toolBean);
        }
        registerMcpServers(toolkit, app.getMcpServerIds());
        return toolkit;
    }

    private void registerMcpServers(Toolkit toolkit, List<String> mcpServerIds) {
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return;
        }
        for (String mcpId : mcpServerIds) {
            try {
                McpServerEntity server = mcpServerService.loadById(mcpId);
                if (Boolean.FALSE.equals(server.getEnabled())) {
                    log.warn("MCP 服务 {} 已禁用，跳过装载", mcpId);
                    continue;
                }
                if (Boolean.FALSE.equals(server.getEnableTools())) {
                    log.warn("MCP 服务 {} 未启用工具，跳过装载", mcpId);
                    continue;
                }
                McpClientWrapper wrapper = mcpServerService.buildWrapper(server);
                toolkit.registration().mcpClient(wrapper).apply();
                log.info("已装载 MCP 服务 [{}] 的工具", server.getName());
            } catch (Exception e) {
                log.error("装载 MCP 服务 {} 失败: {}", mcpId, e.getMessage());
            }
        }
    }

    private boolean hasToolMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}
