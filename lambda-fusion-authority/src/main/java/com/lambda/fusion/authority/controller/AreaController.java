package com.lambda.fusion.authority.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lambda.fusion.authority.model.area.*;
import com.lambda.fusion.authority.service.AreaService;
import com.lambda.fusion.core.FusionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行政区划管理API
 */
@RestController
@RequestMapping("/authority/areas")
@Tag(name = "区域管理")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000005")
    @GetMapping
    @Operation(summary = "查询区域列表")
    public List<Area> list(AreaQuery query) {
        return areaService.getAreas(query);
    }

  @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000006")
    @GetMapping("tree")
    @Operation(summary = "获取区域树形结构")
    public List<AreaTree> tree(
            @Parameter(description = "父区域编码，不传则获取顶级") @RequestParam(required = false) String parentCode) {
        return areaService.getAreaTree(parentCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000007")
    @GetMapping("/{areaCode}")
    @Operation(summary = "根据区域编码查询")
    public Area get(@PathVariable @Parameter(description = "区域编码", required = true) String areaCode) {
        return areaService.getByAreaCode(areaCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000008")
    @GetMapping("/{parentCode}/children")
    @Operation(summary = "获取下级区域")
    public List<Area> children(@PathVariable @Parameter(description = "父区域编码", required = true) String parentCode) {
        return areaService.getChildren(parentCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000009")
    @GetMapping("/{areaCode}/check")
    @Operation(summary = "检查区域编码是否存在")
    public Boolean check(@PathVariable @Parameter(description = "区域编码", required = true) String areaCode) {
        return areaService.checkAreaCode(areaCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000010")
    @PostMapping
    @Operation(summary = "新增区域")
    public Area add(@Parameter(description = "区域信息", required = true) @Valid @RequestBody CreateArea createArea) {
        return areaService.addArea(createArea);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000011")
    @PutMapping("/{areaCode}")
    @Operation(summary = "更新区域")
    public Area update(
            @PathVariable @Parameter(description = "区域编码", required = true) String areaCode,
            @Parameter(description = "区域信息", required = true) @Valid @RequestBody UpdateArea updateArea) {
        updateArea.setAreaCode(areaCode);
        return areaService.updateArea(updateArea);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value="T1000000012")
    @DeleteMapping("/{areaCode}")
    @Operation(summary = "删除区域")
    public void delete(@PathVariable @Parameter(description = "区域编码", required = true) String areaCode) {
        areaService.deleteArea(areaCode);
    }
}
