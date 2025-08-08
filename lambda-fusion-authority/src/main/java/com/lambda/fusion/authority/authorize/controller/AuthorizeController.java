package com.lambda.fusion.authority.authorize.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.authorize.model.dto.NavigationQueryDTO;
import com.lambda.fusion.authority.authorize.service.AuthorizeService;
import com.lambda.fusion.authority.resource.model.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@SaCheckLogin
@Controller
@RequiredArgsConstructor
public class AuthorizeController {

    private final AuthorizeService authorizeService;

    @GetMapping("/navigation")
    @Operation(
            summary = "获取当前登陆用户的导航菜单列表",
            description = "获取当前登陆用户的导航菜单列表",
            parameters = {
                @Parameter(name = "parentId", description = "父菜单ID"),
                @Parameter(name = "level", description = "菜单层级"),
                @Parameter(name = "mode", description = "资源模式(0:系统资源,1:App资源)")
            })
    public List<Resource> navigation(@Parameter NavigationQueryDTO parameter) {
        LoginUser operator = OperatorUtils.getOperator();
        return authorizeService.getNavigation(operator, parameter);
    }
}
