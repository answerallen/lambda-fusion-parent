package com.lambda.fusion.ai.skill.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.skill.mapper.SkillMapper;
import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillPage;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import com.lambda.fusion.ai.skill.model.entity.SkillEntity;
import com.lambda.fusion.ai.skill.service.SkillService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台技能市场服务实现（mapper-direct，对标 {@code McpServerServiceImpl}）。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SkillServiceImpl implements SkillService {

    private final SkillMapper skillMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Page<SkillEntity> page(SkillPage query) {
        return skillMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public SkillEntity get(String id) {
        return requireExists(id);
    }

    @Override
    public SkillEntity getByName(String name) {
        return skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getName, name));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity create(CreateSkill dto) {
        ensureNameUnique(dto.getName(), null);
        SkillEntity entity = new SkillEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setVersion(dto.getVersion());
        entity.setMarkdown(dto.getMarkdown());
        entity.setResources(dto.getResources());
        entity.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        entity.setRemark(dto.getRemark());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        skillMapper.insert(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateSkill dto) {
        SkillEntity entity = requireExists(id);
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getVersion() != null) {
            entity.setVersion(dto.getVersion());
        }
        if (dto.getMarkdown() != null) {
            entity.setMarkdown(dto.getMarkdown());
        }
        if (dto.getResources() != null) {
            entity.setResources(dto.getResources());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        requireExists(id);
        skillMapper.deleteById(id);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
    }

    @Override
    public SkillEntity loadById(String id) {
        return requireExists(id);
    }

    private SkillEntity requireExists(String id) {
        SkillEntity entity = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getId, id));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SKILL_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String name, String excludeId) {
        boolean exists = skillMapper.exists(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getName, name)
                .ne(excludeId != null, SkillEntity::getId, excludeId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.SKILL_NAME_EXISTS, name);
        }
    }
}
