package com.lambda.fusion.ai.agent.runtime;

import java.util.List;

/**
 * 工具组配置（{@code ai_app.tool_groups} JSON 数组的单个元素）。经 AgentScope {@code Toolkit}
 * {@code createToolGroup} + {@code ToolRegistration.group} 把工具按组绑定，{@code updateToolGroups}
 * 控制组激活/停用，实现 app 级动态工具分组。
 *
 * @author Jin
 */
public record ToolGroupSpecDto(String name, String description, Boolean active, List<String> toolNames) {}
