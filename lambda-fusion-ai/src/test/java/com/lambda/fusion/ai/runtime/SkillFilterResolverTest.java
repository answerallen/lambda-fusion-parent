package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import io.agentscope.core.skill.SkillFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link AgentFactory#resolveSkillFilter} 按 app 的 skillsAllow/skillsDeny 解析 harness
 * {@link SkillFilter}：allow 仅放行列表、deny 屏蔽列表、均空全放行、allow 优先于 deny。
 *
 * @author Jin
 */
class SkillFilterResolverTest {

    @Test
    void allowOnlyPermitsListedSkills() {
        AppEntity app = new AppEntity();
        app.setSkillsAllow(List.of("a", "b"));

        SkillFilter filter = AgentFactory.resolveSkillFilter(app);

        assertThat(filter.isAllowed("a")).isTrue();
        assertThat(filter.isAllowed("b")).isTrue();
        assertThat(filter.isAllowed("c")).isFalse();
    }

    @Test
    void denyBlocksListedSkills() {
        AppEntity app = new AppEntity();
        app.setSkillsDeny(List.of("x"));

        SkillFilter filter = AgentFactory.resolveSkillFilter(app);

        assertThat(filter.isAllowed("x")).isFalse();
        assertThat(filter.isAllowed("y")).isTrue();
    }

    @Test
    void emptyAllowsAll() {
        AppEntity app = new AppEntity();

        SkillFilter filter = AgentFactory.resolveSkillFilter(app);

        assertThat(filter.isAllowed("anything")).isTrue();
    }

    @Test
    void allowTakesPrecedenceOverDeny() {
        AppEntity app = new AppEntity();
        app.setSkillsAllow(List.of("a"));
        app.setSkillsDeny(List.of("a", "b"));

        SkillFilter filter = AgentFactory.resolveSkillFilter(app);

        // allow 优先 -> 仅 "a" 放行
        assertThat(filter.isAllowed("a")).isTrue();
        assertThat(filter.isAllowed("b")).isFalse();
    }
}
