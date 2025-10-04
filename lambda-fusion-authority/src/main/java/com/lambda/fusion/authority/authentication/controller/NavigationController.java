package com.lambda.fusion.authority.authentication.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.authentication.model.dto.NavigationQueryDTO;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import com.lambda.fusion.authority.resource.model.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 负责用户认证相关的HTTP接口
 */
@Tag(name = "用户认证", description = "用户认证相关接口")
@SaCheckLogin
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class NavigationController {

    private final AuthenticationService authenticationService;

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
    public List<Resource> getNavigation(@Parameter NavigationQueryDTO query) {
        LoginUser operator = OperatorUtils.getOperator();
        return authenticationService.getNavigation(operator, query);
    }
}
