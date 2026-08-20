package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.SubAgentWorkspaceMode;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import org.apache.commons.lang3.StringUtils;

/**
 * 将子代理实体转换为 Harness {@link SubagentDeclaration}。
 * {@code prompt} 映射为内联指令，{@code modelId} 由 {@link ModelResolver} 解析；未指定模型时继承主 Agent。
 * 其余可选参数为空时不写入声明，由 Harness 使用默认值或继承主 Agent 配置。
 *
 * @author Jin
 */
public final class SubAgentDeclarationMapper {

    private SubAgentDeclarationMapper() {}

    static SubagentDeclaration toDeclaration(SubAgentEntity entity) {
        SubagentDeclaration.Builder builder = SubagentDeclaration.builder()
                .name(entity.getName())
                // 主 Agent 根据 description 选择子代理，保存入口已保证该字段非空。
                .description(entity.getDescription())
                .inlineAgentsBody(entity.getPrompt());
        if (StringUtils.isNotBlank(entity.getModelId())) {
            builder.model(entity.getModelId());
        }
        if (entity.getSteps() != null && entity.getSteps() > 0) {
            builder.steps(entity.getSteps());
        }
        if (entity.getTemperature() != null) {
            builder.temperature(entity.getTemperature().doubleValue());
        }
        if (entity.getTopP() != null) {
            builder.topP(entity.getTopP().doubleValue());
        }
        if (entity.getToolsAllow() != null && !entity.getToolsAllow().isEmpty()) {
            builder.tools(entity.getToolsAllow());
        }
        if (entity.getSkillsAllow() != null && !entity.getSkillsAllow().isEmpty()) {
            builder.skills(entity.getSkillsAllow());
        }
        builder.workspaceMode(
                SubAgentWorkspaceMode.of(entity.getWorkspaceMode()) == SubAgentWorkspaceMode.SHARED
                        ? WorkspaceMode.SHARED
                        : WorkspaceMode.ISOLATED);
        return builder.build();
    }
}
