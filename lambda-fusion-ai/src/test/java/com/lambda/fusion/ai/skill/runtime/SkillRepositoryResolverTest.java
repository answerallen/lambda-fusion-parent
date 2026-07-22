package com.lambda.fusion.ai.skill.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.skill.SkillRepositoryProvider;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link SkillRepositoryResolver} 按 type 选匹配 provider 并缓存；NONE/无匹配返回 null。
 *
 * @author Jin
 */
class SkillRepositoryResolverTest {

    @Test
    void returnsMatchingProviderRepo() {
        AgentSkillRepository repoA = noopRepo();
        AgentSkillRepository repoB = noopRepo();
        SkillRepositoryResolver resolver = new SkillRepositoryResolver(
                props("MYSQL"), List.of(provider("MYSQL", repoA), provider("POSTGRES", repoB)));

        assertThat(resolver.resolve()).isSameAs(repoA);
    }

    @Test
    void cachesResult() {
        AgentSkillRepository repo = noopRepo();
        SkillRepositoryResolver resolver =
                new SkillRepositoryResolver(props("MYSQL"), List.of(provider("MYSQL", repo)));

        assertThat(resolver.resolve()).isSameAs(repo);
        assertThat(resolver.resolve()).isSameAs(repo);
    }

    @Test
    void noneReturnsNull() {
        SkillRepositoryResolver resolver = new SkillRepositoryResolver(props("NONE"), List.of());

        assertThat(resolver.resolve()).isNull();
    }

    @Test
    void noMatchReturnsNull() {
        SkillRepositoryResolver resolver =
                new SkillRepositoryResolver(props("GIT"), List.of(provider("MYSQL", noopRepo())));

        assertThat(resolver.resolve()).isNull();
    }

    private static AiProperties props(String type) {
        AiProperties p = new AiProperties();
        p.getSkill().getRepository().setType(type);
        return p;
    }

    private static SkillRepositoryProvider provider(String type, AgentSkillRepository repo) {
        return new SkillRepositoryProvider() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public AgentSkillRepository create() {
                return repo;
            }
        };
    }

    private static AgentSkillRepository noopRepo() {
        return new AgentSkillRepository() {
            @Override
            public AgentSkill getSkill(String name) {
                return null;
            }

            @Override
            public List<String> getAllSkillNames() {
                return List.of();
            }

            @Override
            public List<AgentSkill> getAllSkills() {
                return List.of();
            }

            @Override
            public boolean save(List<AgentSkill> skills, boolean overwrite) {
                return false;
            }

            @Override
            public boolean delete(String name) {
                return false;
            }

            @Override
            public boolean skillExists(String name) {
                return false;
            }

            @Override
            public AgentSkillRepositoryInfo getRepositoryInfo() {
                return null;
            }

            @Override
            public String getSource() {
                return "stub";
            }

            @Override
            public void setWriteable(boolean writeable) {}

            @Override
            public boolean isWriteable() {
                return false;
            }
        };
    }
}
