package com.lambda.fusion.ai.skill.runtime;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.skill.SkillRepositoryProvider;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技能仓库解析器：按 {@code lambda.fusion.ai.skill.repository.type} 选匹配的 {@link SkillRepositoryProvider}
 * 构造 {@link AgentSkillRepository} 并缓存（共享单例）。{@code type=NONE}、无匹配 provider 或构造失败时返回 null
 * （技能市场禁用，WORKSPACE app 仅用 workspace 本地技能）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRepositoryResolver {

    private final AiProperties aiProperties;
    private final List<SkillRepositoryProvider> providers;

    private volatile AgentSkillRepository cached;
    private volatile boolean resolved = false;

    /** 解析并缓存技能仓库；未启用返回 null。 */
    public AgentSkillRepository resolve() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    cached = build();
                    resolved = true;
                    if (cached == null) {
                        log.info(
                                "技能市场未启用（type={} 无匹配 provider 或显式禁用）",
                                aiProperties.getSkill().getRepository().getType());
                    }
                }
            }
        }
        return cached;
    }

    private AgentSkillRepository build() {
        String type = aiProperties.getSkill().getRepository().getType();
        if (type == null || type.isBlank() || "NONE".equalsIgnoreCase(type)) {
            return null;
        }
        try {
            return providers.stream()
                    .filter(p -> p.type().equalsIgnoreCase(type))
                    .findFirst()
                    .map(SkillRepositoryProvider::create)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("技能仓库 {} 创建失败: {}", type, e.getMessage());
            return null;
        }
    }
}
