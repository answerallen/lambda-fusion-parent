package com.lambda.fusion.authority.authentication.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lambda.fusion.authority.authentication.model.AuthenticatedUser;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.model.NavigationRoute;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证接口控制器", description = "提供用户认证相关的RESTful API，包括获取当前登录用户的导航菜单列表和用户详细信息。")
@SaCheckLogin
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @GetMapping("/navigation")
    @Operation(
            summary = "获取当前登录用户的导航菜单列表",
            description = "根据当前登录用户的权限获取可访问的导航菜单列表",
            parameters = {
                @Parameter(name = "parentId", description = "父菜单ID"),
                @Parameter(name = "level", description = "菜单层级"),
                @Parameter(name = "mode", description = "资源模式(0:系统资源,1:App资源)")
            })
    public List<NavigationRoute> getNavigation(@Parameter NavigationQuery query) {
        return authenticationService.getNavigation(query);
    }

    @GetMapping("/userinfo")
    @Operation(summary = "获取当前登录用户详细信息", description = "返回一个包含当前登录用户详细信息的 AuthenticatedUser 对象")
    public AuthenticatedUser getUserInfo() {
        return authenticationService.getUserInfo();
    }

    @GetMapping("/authorities")
    @Operation(summary = "获取当前用户的权限码集合", description = "返回当前登录用户拥有的所有权限标识符列表")
    public List<String> getAuthorities() {
        return authenticationService.getAuthorities();
    }
}
