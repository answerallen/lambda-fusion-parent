package com.lambda.fusion.ai.skill.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.skill.model.CreateSkill;
import com.lambda.fusion.ai.skill.model.SkillView;
import com.lambda.fusion.ai.skill.model.UpdateSkill;
import com.lambda.fusion.ai.skill.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

    @Operation(summary = "列出全部技能")
    @GetMapping
    public List<SkillView> list() {
        return skillService.list();
    }

    @Operation(summary = "查询技能")
    @GetMapping("/{name}")
    public SkillView get(@Parameter(description = "技能名", required = true) @PathVariable String name) {
        return skillService.get(name);
    }

    @OperationLog
    @Operation(summary = "新增技能")
    @PostMapping
    public SkillView create(@RequestBody @Valid CreateSkill dto) {
        return skillService.create(dto);
    }

    @OperationLog
    @Operation(summary = "更新技能")
    @PutMapping("/{name}")
    public void update(
            @Parameter(description = "技能名", required = true) @PathVariable String name,
            @RequestBody @Valid UpdateSkill dto) {
        skillService.update(name, dto);
    }

    @OperationLog
    @Operation(summary = "删除技能")
    @DeleteMapping("/{name}")
    public void delete(@Parameter(description = "技能名", required = true) @PathVariable String name) {
        skillService.delete(name);
    }
}
