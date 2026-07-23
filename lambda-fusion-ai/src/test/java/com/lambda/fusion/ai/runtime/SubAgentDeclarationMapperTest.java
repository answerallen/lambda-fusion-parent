package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link SubAgentDeclarationMapper#toDeclaration}：全字段映射（modelId→model、
 * prompt→inlineAgentsBody、toolsAllow/skillsAllow 透传、workspaceMode 转换）与空值省略。
 *
 * @author Jin
 */
class SubAgentDeclarationMapperTest {

    @Test
    void mapsAllFields() {
        SubAgentEntity entity = new SubAgentEntity();
        entity.setName("writer");
        entity.setDescription("擅长文案撰写与润色");
        entity.setPrompt("你是一名专业文案助手");
        entity.setModelId("model-1");
        entity.setSteps(5);
        entity.setTemperature(new BigDecimal("0.70"));
        entity.setTopP(new BigDecimal("0.90"));
        entity.setToolsAllow(List.of("web_search"));
        entity.setSkillsAllow(List.of("copywriting"));
        entity.setWorkspaceMode("SHARED");

        SubagentDeclaration declaration = SubAgentDeclarationMapper.toDeclaration(entity);

        assertThat(declaration.getName()).isEqualTo("writer");
        assertThat(declaration.getDescription()).isEqualTo("擅长文案撰写与润色");
        assertThat(declaration.getInlineAgentsBody()).isEqualTo("你是一名专业文案助手");
        assertThat(declaration.getModel()).isEqualTo("model-1");
        assertThat(declaration.getSteps()).isEqualTo(5);
        assertThat(declaration.getTemperature()).isEqualTo(0.7);
        assertThat(declaration.getTopP()).isEqualTo(0.9);
        assertThat(declaration.getTools()).containsExactly("web_search");
        assertThat(declaration.getSkills()).containsExactly("copywriting");
        assertThat(declaration.getWorkspaceMode()).isEqualTo(WorkspaceMode.SHARED);
    }

    @Test
    void omitsNullAndEmptyFields() {
        SubAgentEntity entity = new SubAgentEntity();
        entity.setName("helper");
        entity.setDescription("通用助手");
        entity.setPrompt("你是通用助手");

        SubagentDeclaration declaration = SubAgentDeclarationMapper.toDeclaration(entity);

        // 空值省略：模型继承主 agent、steps/采样参数走 harness 默认（steps 默认 10）、工具/技能全继承
        assertThat(declaration.getModel()).isNull();
        assertThat(declaration.getSteps()).isEqualTo(10);
        assertThat(declaration.getTemperature()).isNull();
        assertThat(declaration.getTopP()).isNull();
        assertThat(declaration.getTools()).isNullOrEmpty();
        assertThat(declaration.getSkills()).isNullOrEmpty();
        // workspaceMode 空按 ISOLATED
        assertThat(declaration.getWorkspaceMode()).isEqualTo(WorkspaceMode.ISOLATED);
    }

    @Test
    void nonPositiveStepsOmitted() {
        SubAgentEntity entity = new SubAgentEntity();
        entity.setName("helper");
        entity.setDescription("通用助手");
        entity.setPrompt("你是通用助手");
        entity.setSteps(0);

        // steps<=0 不下发，回落 harness 默认值 10
        assertThat(SubAgentDeclarationMapper.toDeclaration(entity).getSteps()).isEqualTo(10);
    }
}
