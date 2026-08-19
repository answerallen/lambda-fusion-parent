package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.SubAgentWorkspaceMode;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import org.apache.commons.lang3.StringUtils;

/**
 * 子代理实体 → harness {@link SubagentDeclaration} 纯函数转换（包级静态便于单测）。
 * 字段映射：{@code prompt → inlineAgentsBody}；{@code modelId → model}（fusion 模型ID，
 * 经 modelResolver 桥接到 {@link ModelResolver}，空 = 继承主 agent 模型）；
 * steps/temperature/topP/toolsAllow/skillsAllow 空值省略（走 harness 默认/全继承）。
 *
 * @author Jin
 */
public final class SubAgentDeclarationMapper {

    private SubAgentDeclarationMapper() {}

    static SubagentDeclaration toDeclaration(SubAgentEntity entity) {
        SubagentDeclaration.Builder builder = SubagentDeclaration.builder()
                .name(entity.getName())
                // description 是主 agent 路由的唯一依据（保存时已强制非空）
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
