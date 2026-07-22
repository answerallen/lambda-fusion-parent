package com.lambda.fusion.ai.skill.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillPage;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import com.lambda.fusion.ai.skill.model.entity.SkillEntity;

/**
 * 平台技能市场服务：CRUD（admin 管理）。变更后发 {@code ConfigChangedEvent.all()} 失效 agent 缓存，
 * 使 WORKSPACE app 下次构建读取最新市场技能。
 *
 * @author Jin
 */
public interface SkillService {

    Page<SkillEntity> page(SkillPage query);

    SkillEntity get(String id);

    SkillEntity getByName(String name);

    SkillEntity create(CreateSkill dto);

    void update(String id, UpdateSkill dto);

    void delete(String id);

    SkillEntity loadById(String id);
}
