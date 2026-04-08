package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.LlmModel;
import com.lambda.fusion.ai.model.RegisterModel;
import com.lambda.fusion.ai.service.LlmModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/llm-models")
@Tag(name = "LLM模型管理")
@RequiredArgsConstructor
public class LlmModelController {

    private final LlmModelService llmModelService;

    @PostMapping
    @Operation(summary = "注册模型")
    public LlmModel register(@Valid @RequestBody RegisterModel registerModel) {
        return llmModelService.registerModel(registerModel);
    }

    @GetMapping
    @Operation(summary = "查询所有模型")
    public List<LlmModel> listAll() {
        return llmModelService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模型详情")
    public LlmModel getById(@PathVariable String id) {
        return llmModelService.getModelById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型")
    public void update(@PathVariable String id, @Valid @RequestBody RegisterModel dto) {
        llmModelService.updateModel(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型")
    public void delete(@PathVariable String id) {
        llmModelService.deleteModel(id);
    }
}
