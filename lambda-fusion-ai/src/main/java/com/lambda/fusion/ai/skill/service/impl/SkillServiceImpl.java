package com.lambda.fusion.ai.skill.service.impl;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillView;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import com.lambda.fusion.ai.skill.runtime.SkillRepositoryResolver;
import com.lambda.fusion.ai.skill.service.SkillService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 技能市场服务实现：CRUD 经 {@link SkillRepositoryResolver} 解析的 {@link AgentSkillRepository}。
 *
 * <p>仓库未配置 -> {@link AiErrorCode#CONFIGURATION_ERROR}；只读仓库写操作 ->
 * {@link AiErrorCode#OPERATION_NOT_SUPPORTED}。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SkillServiceImpl implements SkillService {

    private static final String SOURCE = "marketplace";

    private final SkillRepositoryResolver skillRepositoryResolver;

    @Override
    public List<SkillView> list() {
        AgentSkillRepository repo = requireRepo();
        return repo.getAllSkills().stream().map(SkillServiceImpl::toView).toList();
    }

    @Override
    public SkillView get(String name) {
        AgentSkillRepository repo = requireRepo();
        AgentSkill skill = repo.getSkill(name);
        if (skill == null) {
            throw new AiBusinessException(AiErrorCode.SKILL_NOT_FOUND, name);
        }
        return toView(skill);
    }

    @Override
    public SkillView create(CreateSkill dto) {
        AgentSkillRepository repo = requireWritable();
        if (repo.skillExists(dto.getName())) {
            throw new AiBusinessException(AiErrorCode.SKILL_NAME_EXISTS, dto.getName());
        }
        AgentSkill skill = toAgentSkill(dto.getName(), dto.getDescription(), dto.getMarkdown(), dto.getResources());
        repo.save(List.of(skill), false);
        return toView(skill);
    }

    @Override
    public void update(String name, UpdateSkill dto) {
        AgentSkillRepository repo = requireWritable();
        AgentSkill existing = repo.getSkill(name);
        if (existing == null) {
            throw new AiBusinessException(AiErrorCode.SKILL_NOT_FOUND, name);
        }
        String description = dto.getDescription() != null ? dto.getDescription() : existing.getDescription();
        String markdown = dto.getMarkdown() != null ? dto.getMarkdown() : existing.getSkillContent();
        Map<String, String> resources = dto.getResources() != null ? dto.getResources() : existing.getResources();
        repo.save(List.of(toAgentSkill(name, description, markdown, resources)), true);
    }

    @Override
    public void delete(String name) {
        AgentSkillRepository repo = requireWritable();
        if (!repo.skillExists(name)) {
            throw new AiBusinessException(AiErrorCode.SKILL_NOT_FOUND, name);
        }
        repo.delete(name);
    }

    private AgentSkillRepository requireRepo() {
        AgentSkillRepository repo = skillRepositoryResolver.resolve();
        if (repo == null) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, "技能仓库未配置");
        }
        return repo;
    }

    private AgentSkillRepository requireWritable() {
        AgentSkillRepository repo = requireRepo();
        if (!repo.isWriteable()) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "技能仓库只读，不支持写操作");
        }
        return repo;
    }

    /**
     * @param description 调用方需保证非空
     */
    static AgentSkill toAgentSkill(String name, String description, String markdown, Map<String, String> resources) {
        Map<String, String> res = resources == null ? Map.of() : resources;
        return new AgentSkill(name, description, markdown, res, SOURCE);
    }

    static SkillView toView(AgentSkill skill) {
        SkillView view = new SkillView();
        view.setName(skill.getName());
        view.setDescription(skill.getDescription());
        view.setMarkdown(skill.getSkillContent());
        view.setResources(skill.getResources());
        return view;
    }
}
