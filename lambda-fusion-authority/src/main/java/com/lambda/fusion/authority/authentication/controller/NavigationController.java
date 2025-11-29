package com.lambda.fusion.authority.authentication.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.service.AuthService;
import com.lambda.fusion.authority.resource.model.ResourceTree;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导航菜单
 * 导航菜单接口
 */
@Tag(name = "导航菜单", description = "获取当前用户导航菜单接口")
@SaCheckLogin
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class NavigationController {

    private final AuthService authService;

    /**
     * 获取当前登录用户的导航菜单列表
     *
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    @GetMapping("/navigation")
    @Operation(
            summary = "获取当前登录用户的导航菜单列表",
            description = "根据当前登录用户的权限获取可访问的导航菜单列表",
            parameters = {
                @Parameter(name = "parentId", description = "父菜单ID"),
                @Parameter(name = "level", description = "菜单层级"),
                @Parameter(name = "mode", description = "资源模式(0:系统资源,1:App资源)")
            })
    public List<ResourceTree> getNavigation(@Parameter NavigationQuery query) {
        LoginUser operator = OperatorUtils.getOperator();
        return authService.getNavigation(operator, query);
    }
}
