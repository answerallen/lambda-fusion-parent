package com.lambda.fusion.ai.skill.service;

import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillView;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import java.util.List;

/**
 * 技能市场服务：经配置选定的 {@code AgentSkillRepository} 做 CRUD（list/get/create/update/delete）。
 * 仓库未配置 -> 抛配置错误；只读仓库 -> 写操作抛不支持。
 *
 * @author Jin
 */
public interface SkillService {

    List<SkillView> list();

    SkillView get(String name);

    SkillView create(CreateSkill dto);

    void update(String name, UpdateSkill dto);

    void delete(String name);
}
