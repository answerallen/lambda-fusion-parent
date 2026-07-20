package com.lambda.fusion.ai.apps.controller;

import com.lambda.fusion.ai.apps.model.App;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.service.AppsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai/apps")
@Tag(name = "AI 应用管理")
@RequiredArgsConstructor
public class AppsController {

    private final AppsService appsService;

    @PostMapping
    @Operation(summary = "创建新的AI应用")
    public App createApp(@Valid @RequestBody CreateApp dto) {
        return appsService.createApp(dto);
    }

    @PutMapping
    @Operation(summary = "更新AI应用")
    public App updateApp(@Valid @RequestBody UpdateApp dto) {
        return appsService.updateApp(dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取AI应用详情")
    public App getApp(@PathVariable String id) {
        return appsService.getAppById(id);
    }

    @GetMapping
    @Operation(summary = "获取所有AI应用列表")
    public List<App> listApps() {
        return appsService.listApps();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除AI应用")
    public Boolean deleteApp(@PathVariable String id) {
        appsService.deleteApp(id);
        return true;
    }
}
