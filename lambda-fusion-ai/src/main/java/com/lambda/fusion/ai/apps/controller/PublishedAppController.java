package com.lambda.fusion.ai.apps.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.lambda.fusion.ai.apps.model.AvailableApp;
import com.lambda.fusion.ai.apps.model.PublishedAppProfile;
import com.lambda.fusion.ai.apps.service.AppPublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布应用访问入口。profile 匿名可读（仅名片，不替代授权）；access 需登录，做发布态、
 * 启用态与受众校验后返回安全视图。会话、Run、附件仍走既有受保护接口。
 *
 * @author Jin
 */
@Tag(name = "发布应用访问")
@RestController
@RequestMapping("/v1/ai/public/apps")
@RequiredArgsConstructor
public class PublishedAppController {

    private final AppPublicationService appPublicationService;

    @SaIgnore
    @Operation(summary = "匿名查询发布应用公开资料")
    @GetMapping("/{publishCode}/profile")
    public PublishedAppProfile profile(
            @Parameter(description = "发布代码", required = true) @PathVariable String publishCode) {
        return appPublicationService.profile(publishCode);
    }

    @Operation(summary = "登录后校验可否访问发布应用并返回安全视图")
    @GetMapping("/{publishCode}/access")
    public AvailableApp access(@Parameter(description = "发布代码", required = true) @PathVariable String publishCode) {
        return appPublicationService.access(publishCode);
    }
}
