package com.lambda.fusion.ai.skill.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.skill.mapper.SkillMapper;
import com.lambda.fusion.ai.skill.model.entity.SkillEntity;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DB 驱动的技能市场仓库：读取 {@code ai_skill} 启用行为 harness {@link AgentSkill}，作为 WORKSPACE 型
 * app 的额外技能源（与 workspace 文件系统技能 repo 并存）。
 *
 * <p>只读（市场由 admin 经 {@code SkillController} 管理）；agent 自撰写技能走 workspace 文件系统 repo。
 * 共享单例，{@code close()} 为 no-op（不关 mapper）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbSkillRepository implements AgentSkillRepository {

    private static final String SOURCE = "db";

    private final SkillMapper skillMapper;

    @Override
    public AgentSkill getSkill(String name) {
        SkillEntity entity =
                skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getName, name));
        return entity == null ? null : toAgentSkill(entity);
    }

    @Override
    public List<String> getAllSkillNames() {
        return skillMapper
                .selectList(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getEnabled, Boolean.TRUE))
                .stream()
                .map(SkillEntity::getName)
                .toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return skillMapper
                .selectList(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getEnabled, Boolean.TRUE))
                .stream()
                .map(DbSkillRepository::toAgentSkill)
                .toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
        throw new UnsupportedOperationException("DB 技能市场只读，请经 admin API 管理");
    }

    @Override
    public boolean delete(String name) {
        throw new UnsupportedOperationException("DB 技能市场只读，请经 admin API 管理");
    }

    @Override
    public boolean skillExists(String name) {
        return skillMapper.exists(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getName, name));
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo(SOURCE, "ai_skill", false);
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public void setWriteable(boolean writeable) {
        // 只读仓库，忽略
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    @Override
    public void close() {
        // 共享单例，不关 mapper
    }

    /** entity -> harness {@link AgentSkill}（静态可单测）。description 缺失时回退 name（harness 要求非空）。 */
    static AgentSkill toAgentSkill(SkillEntity entity) {
        Map<String, String> resources = entity.getResources() == null ? Map.of() : entity.getResources();
        String description =
                (entity.getDescription() == null || entity.getDescription().isBlank())
                        ? entity.getName()
                        : entity.getDescription();
        return new AgentSkill(entity.getName(), description, entity.getMarkdown(), resources, SOURCE);
    }
}
