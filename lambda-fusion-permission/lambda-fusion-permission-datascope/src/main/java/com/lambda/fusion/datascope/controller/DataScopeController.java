package com.lambda.fusion.datascope.controller;

import com.lambda.fusion.datascope.model.GrantDataScope;
import com.lambda.fusion.datascope.model.DataScopeNode;
import com.lambda.fusion.datascope.service.DataScopeGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据权限管理")
@RestController
@RequestMapping("/datascope")
@RequiredArgsConstructor
public class DataScopeController {

    private final DataScopeGrantService dataScopeGrantService;

    @GetMapping("/tree/{type}")
    @Operation(summary = "获取数据权限分配树", description = "加载用于分配权限的树形结构（带选中状态）")
    public List<DataScopeNode> getDataScopeTree(
            @PathVariable Integer type,
            @RequestParam("targetId") String targetId,
            @RequestParam("targetType") String targetType) {
        return dataScopeGrantService.getDataScopeTree(type, targetId, targetType);
    }

    @PostMapping("/grant")
    @Operation(summary = "保存数据权限授权结果")
    public void grantDataScope(@RequestBody @Valid GrantDataScope req) {
        dataScopeGrantService.grantDataScope(req);
    }
}
