package com.lambda.fusion.authority.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.commons.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.client.ClientQuery;
import com.lambda.fusion.authority.model.client.UpsertClient;
import com.lambda.fusion.authority.model.resource.ApiPermissionTreeNode;
import com.lambda.fusion.authority.service.ClientService;
import com.lambda.fusion.core.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/authority/clients"})
@Tag(name = "客户端管理")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

    @SaCheckPermission(value = "T1000000013")
    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询客户端列表", description = "分页查询客户端列表")
    public Page<ClientEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid ClientQuery clientQuery) {
        if (number != null) {
            clientQuery.setPageNum(number);
        }
        if (size != null) {
            clientQuery.setPageSize(size);
        }
        clientQuery.setTenantId(AuthUtils.getTenantId());
        return clientService.page(clientQuery.getPage(), clientQuery.getLambdaQueryWrapper());
    }

    @SaCheckPermission(value = "T1000000014")
    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询客户端信息", description = "根据id查询客户端信息")
    public ClientEntity get(@Parameter(description = "编号", required = true) @PathVariable String id) {
        return clientService.getById(id);
    }

    @SaCheckPermission(value = "T1000000015")
    @PostMapping
    @Operation(summary = "新增客户端信息", description = "新增客户端信息")
    public void save(@Parameter(description = "客户端信息", required = true) @Valid @RequestBody UpsertClient upsertClient) {
        ClientEntity clientEntity = upsertClient.toEntity();
        clientEntity.setTenantId(AuthUtils.getTenantId());
        clientService.save(clientEntity);
    }

    @SaCheckPermission(value = "T1000000014")
    @GetMapping("/{id}/api-permissions")
    @Operation(summary = "查询客户端接口权限树", description = "按应用与分组返回客户端接口权限树")
    public List<ApiPermissionTreeNode> listApiPermissions(
            @PathVariable @Parameter(description = "客户端编号", required = true) String id,
            @RequestParam(required = false) @Parameter(description = "应用名称") String application,
            @RequestParam(required = false) @Parameter(description = "接口名称") String name) {
        return clientService.listApiPermissions(id, application, name);
    }

    @SaCheckPermission(value = "T1000000016")
    @PutMapping("/{id}")
    @Operation(summary = "更新客户端信息", description = "更新客户端信息")
    public void update(
            @Parameter(description = "客户端编号", required = true) @PathVariable String id,
            @Parameter(description = "客户端信息", required = true) @RequestBody @Valid UpsertClient upsertClient) {
        ClientEntity original = clientService.getById(id);
        if (original == null) {
            throw AuthorityBusinessException.clientNotFound(id);
        }
        ClientEntity clientEntity = upsertClient.toEntity();
        clientEntity.setId(id);
        clientEntity.setSecret(original.getSecret());
        clientEntity.setTenantId(original.getTenantId());
        clientService.updateById(clientEntity);
    }

    @SaCheckPermission(value = "T1000000016")
    @PutMapping("/{id}/api-permissions/{permissionId}")
    @Operation(summary = "绑定客户端接口权限", description = "将指定接口权限绑定到客户端")
    public void bindApiPermission(
            @PathVariable @Parameter(description = "客户端编号", required = true) String id,
            @PathVariable @Parameter(description = "接口权限ID", required = true) String permissionId) {
        clientService.bindApiPermission(AuthUtils.getUser(), id, permissionId);
    }

    @SaCheckPermission(value = "T1000000016")
    @DeleteMapping("/{id}/api-permissions/{permissionId}")
    @Operation(summary = "解绑客户端接口权限", description = "将指定接口权限从客户端解绑")
    public void unbindApiPermission(
            @PathVariable @Parameter(description = "客户端编号", required = true) String id,
            @PathVariable @Parameter(description = "接口权限ID", required = true) String permissionId) {
        clientService.unbindApiPermission(AuthUtils.getUser(), id, permissionId);
    }

    @SaCheckPermission(value = "T1000000017")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户端信息", description = "根据编号删除客户端信息")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ClientEntity client = clientService.getById(id);
        if (client == null) {
            throw AuthorityBusinessException.clientNotFound(id);
        }
        clientService.removeById(id);
    }
}
