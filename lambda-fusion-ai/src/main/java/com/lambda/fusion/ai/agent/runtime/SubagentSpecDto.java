package com.lambda.fusion.ai.agent.runtime;

import java.math.BigDecimal;
import java.util.List;

/**
 * 子 agent 配置（{@code ai_app.subagent_spec} JSON 数组的单个元素）。主 agent 经 AgentScope
 * {@code SubAgentTool} 把子 agent 暴露为工具调用。一层不嵌套：子 agent 不再解析自身的
 * {@code subagentSpec}，避免递归循环。
 *
 * @author Jin
 */
public record SubagentSpecDto(
        String name,
        String toolName,
        String description,
        String sysPrompt,
        String modelId,
        BigDecimal temperature,
        Integer maxTokens,
        List<String> kbIds,
        String ragMode,
        List<String> toolIds,
        List<String> mcpServerIds) {}
