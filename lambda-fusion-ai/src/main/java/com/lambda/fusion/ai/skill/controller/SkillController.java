package com.lambda.fusion.ai.skill.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillPage;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import com.lambda.fusion.ai.skill.model.entity.SkillEntity;
import com.lambda.fusion.ai.skill.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole("ROLE_DEV")
@Tag(name = "技能市场管理")
@RestController
@RequestMapping("/v1/ai/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "分页查询技能")
    @GetMapping("/page")
    public Page<SkillEntity> page(@Valid SkillPage query) {
        return skillService.page(query);
    }

    @Operation(summary = "查询技能详情")
    @GetMapping("/{id}")
    public SkillEntity get(@Parameter(description = "技能ID", required = true) @PathVariable String id) {
        return skillService.get(id);
    }

    @Operation(summary = "按技能名查询")
    @GetMapping("/by-name/{name}")
    public SkillEntity getByName(@Parameter(description = "技能名", required = true) @PathVariable String name) {
        return skillService.getByName(name);
    }

    @OperationLog
    @Operation(summary = "新增技能")
    @PostMapping
    public SkillEntity create(@RequestBody @Valid CreateSkill dto) {
        return skillService.create(dto);
    }

    @OperationLog
    @Operation(summary = "更新技能")
    @PutMapping("/{id}")
    public void update(
            @Parameter(description = "技能ID", required = true) @PathVariable String id,
            @RequestBody @Valid UpdateSkill dto) {
        skillService.update(id, dto);
    }

    @OperationLog
    @Operation(summary = "删除技能")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "技能ID", required = true) @PathVariable String id) {
        skillService.delete(id);
    }
}
