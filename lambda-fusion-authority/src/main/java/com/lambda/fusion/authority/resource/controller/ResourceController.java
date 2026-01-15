package com.lambda.fusion.authority.resource.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.resource.model.CreateResource;
import com.lambda.fusion.authority.resource.model.MoveResource;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.resource.model.ResourceTree;
import com.lambda.fusion.authority.resource.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 资源服务API
 */
@RestController
@SaCheckRole({"ROLE_DEV", "ROLE_SYSTEM", "ROLE_ADMIN"})
@RequestMapping({"/authority/resources"})
@Tag(name = "资源管理")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/tree")
    @Operation(
            summary = "以树形的方式获取资源权限列表",
            description = "以树形的方式获取资源权限列表",
            parameters = {
                @Parameter(name = "parentId", description = "父菜单ID"),
                @Parameter(name = "level", description = "菜单层级"),
                @Parameter(name = "mode", description = "资源模式(0:系统资源,1:App资源)")
            })
    public List<ResourceTree> tree(@Parameter NavigationQuery parameter) {
        return resourceService.getChildren(parameter);
    }

    @GetMapping("/list")
    @Operation(summary = "以平铺的方式获取资源权限列表", description = "以平铺的方式获取资源权限列表")
    public List<Resource> list() {
        return resourceService.getAllResources();
    }

    @PostMapping({"/{id}", "/"})
    @Operation(summary = "新增资源信息", description = "当id为非空时新增其子资源信息")
    public Resource add(@PathVariable(required = false) String id, @Validated @RequestBody CreateResource parameter) {
        if (StringUtils.isNotBlank(id)) {
            parameter.setParentId(id);
        }
        return resourceService.addResource(parameter);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除资源信息", description = "根据编号删除指定的资源信息")
    public void delete(@PathVariable @Parameter(description = "资源编号", required = true) String id) {
        resourceService.deleteResource(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新资源信息", description = "根据编号更新指定的资源信息")
    public Resource update(
            @PathVariable @Parameter(description = "资源编号", required = true) String id, @RequestBody Resource resource) {
        resource.setId(id);
        return resourceService.updateResource(resource);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "移动资源",
            description = "移动指定的资源到其它位置",
            parameters = {
                @Parameter(name = "tid", description = "参照对象编号"),
                @Parameter(
                        name = "type",
                        description = "移动类型(0:下级,1:之前,2:之后)",
                        schema = @Schema(allowableValues = {"0", "1", "2"}))
            })
    public void move(
            @PathVariable @Parameter(description = "资源编号", required = true) String id,
            @RequestBody MoveResource parameter) {
        parameter.setId(id);
        resourceService.move(parameter);
    }
}
