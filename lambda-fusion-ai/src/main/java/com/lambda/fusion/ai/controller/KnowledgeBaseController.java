package com.lambda.fusion.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库管理Controller
 *
 * @author Jin
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@Tag(name = "知识库管理", description = "知识库相关接口")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @Operation(summary = "创建知识库", description = "创建一个新的知识库")
    public KnowledgeBase create(@Valid @RequestBody CreateKnowledgeBase createKnowledgeBase) {
        return knowledgeBaseService.createKnowledgeBase(createKnowledgeBase);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询知识库", description = "根据租户ID分页查询知识库列表")
    public Page<KnowledgeBase> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "租户ID", required = true) @RequestParam Long tenantId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return knowledgeBaseService.pageKnowledgeBases(pageNum, pageSize, tenantId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询知识库详情", description = "根据ID查询知识库详细信息")
    public KnowledgeBase getById(@Parameter(description = "知识库ID", required = true) @PathVariable Long id) {
        return knowledgeBaseService.getKnowledgeBaseById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库配置信息")
    public void update(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgeBase dto) {
        knowledgeBaseService.updateKnowledgeBase(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "删除指定的知识库(软删除)")
    public void delete(@Parameter(description = "知识库ID", required = true) @PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
    }

    @GetMapping("/list")
    @Operation(summary = "查询知识库列表", description = "根据租户ID查询知识库列表")
    public List<KnowledgeBase> list(
            @Parameter(description = "租户ID", required = true) @RequestParam Long tenantId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return knowledgeBaseService.listByTenantId(tenantId, status);
    }
}
