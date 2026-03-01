package com.lambda.fusion.authority.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.client.ClientQuery;
import com.lambda.fusion.authority.model.client.UpsertClient;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.service.ClientService;
import com.lambda.fusion.core.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/authority/clients"})
@Tag(name = "客户端管理")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

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
        clientQuery.setTenantId(SecurityUtils.getTenantId());
        return clientService.page(clientQuery.getPage(), clientQuery.getLambdaQueryWrapper());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询客户端信息", description = "根据id查询客户端信息")
    public ClientEntity get(@Parameter(description = "编号", required = true) @PathVariable String id) {
        return clientService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增客户端信息", description = "新增客户端信息")
    public void save(@Parameter(description = "客户端信息", required = true) @Valid @RequestBody UpsertClient upsertClient) {
        ClientEntity clientEntity = upsertClient.toEntity();
        clientService.save(clientEntity);
    }

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
