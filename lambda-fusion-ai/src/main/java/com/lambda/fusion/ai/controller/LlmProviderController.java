package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.CreateLlmProvider;
import com.lambda.fusion.ai.model.LlmProvider;
import com.lambda.fusion.ai.model.UpdateLlmProvider;
import com.lambda.fusion.ai.service.LlmProviderService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/v1/llm-model-providers")
@Tag(name = "LLM提供商管理")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderService llmProviderService;

    @GetMapping
    @Operation(summary = "查询所有提供商")
    public List<LlmProvider> listAll() {
        return llmProviderService.listAll();
    }

    @PostMapping
    @Operation(summary = "创建提供商")
    public String create(@Valid @RequestBody CreateLlmProvider request) {
        return llmProviderService.create(request);
    }

    @PutMapping("/{code}")
    @Operation(summary = "更新提供商")
    public void update(@PathVariable String code, @Valid @RequestBody UpdateLlmProvider request) {
        llmProviderService.update(code, request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "删除提供商")
    public void delete(@PathVariable String code) {
        llmProviderService.delete(code);
    }
}
