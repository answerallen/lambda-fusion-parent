package com.lambda.fusion.ai.skill.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.skill.model.entity.SkillEntity;
import io.agentscope.core.skill.AgentSkill;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link DbSkillRepository#toAgentSkill} 把 {@link SkillEntity} 映射为 harness {@link AgentSkill}。
 *
 * @author Jin
 */
class SkillConverterTest {

    @Test
    void toAgentSkillMapsAllFields() {
        SkillEntity entity = new SkillEntity();
        entity.setName("my-skill");
        entity.setDescription("does X");
        entity.setMarkdown("# My Skill\n\nbody");
        entity.setResources(Map.of("ref.md", "# ref"));

        AgentSkill skill = DbSkillRepository.toAgentSkill(entity);

        assertThat(skill.getName()).isEqualTo("my-skill");
        assertThat(skill.getDescription()).isEqualTo("does X");
        assertThat(skill.getSkillContent()).isEqualTo("# My Skill\n\nbody");
        assertThat(skill.getResources()).containsEntry("ref.md", "# ref");
        assertThat(skill.getSource()).isEqualTo("db");
    }

    @Test
    void toAgentSkillNullResourcesYieldsEmptyMap() {
        SkillEntity entity = new SkillEntity();
        entity.setName("s");
        entity.setDescription("d");
        entity.setMarkdown("body");
        entity.setResources(null);

        AgentSkill skill = DbSkillRepository.toAgentSkill(entity);

        assertThat(skill.getResources()).isEmpty();
    }

    @Test
    void toAgentSkillBlankDescriptionFallsBackToName() {
        SkillEntity entity = new SkillEntity();
        entity.setName("s");
        entity.setDescription(" ");
        entity.setMarkdown("body");

        AgentSkill skill = DbSkillRepository.toAgentSkill(entity);

        assertThat(skill.getDescription()).isEqualTo("s");
    }
}
