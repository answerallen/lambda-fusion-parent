package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.dto.RegisterModelDTO;
import com.lambda.fusion.ai.model.vo.LlmModelVO;
import com.lambda.fusion.ai.service.LlmModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llm-models")
@Tag(name = "LLM模型管理")
@RequiredArgsConstructor
public class LlmModelController {

    private final LlmModelService llmModelService;

    @PostMapping
    @Operation(summary = "注册模型")
    public LlmModelVO register(@Valid @RequestBody RegisterModelDTO dto) {
        return llmModelService.registerModel(dto);
    }

    @GetMapping
    @Operation(summary = "查询所有模型")
    public List<LlmModelVO> listAll() {
        return llmModelService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模型详情")
    public LlmModelVO getById(@PathVariable Long id) {
        return llmModelService.getModelById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型")
    public void update(@PathVariable Long id, @Valid @RequestBody RegisterModelDTO dto) {
        llmModelService.updateModel(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型")
    public void delete(@PathVariable Long id) {
        llmModelService.deleteModel(id);
    }
}
