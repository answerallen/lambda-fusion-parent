package com.lambda.fusion.authority.client.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.client.model.dto.ClientInputDTO;
import com.lambda.fusion.authority.client.model.dto.ClientPageQueryDTO;
import com.lambda.fusion.authority.client.model.entity.ClientEntity;
import com.lambda.fusion.authority.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/clients"})
@Tag(name = "客户端管理")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping("/page")
    @Operation(summary = "分页查询客户端列表", description = "分页查询客户端列表")
    public Page<ClientEntity> page(@Valid @RequestBody ClientPageQueryDTO clientPageQueryDTO) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            clientPageQueryDTO.setTenantId(tenantId);
        }
        return clientService.page(clientPageQueryDTO.getPage(), clientPageQueryDTO.getLambdaQueryWrapper());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询客户端信息", description = "根据id查询客户端信息")
    public ClientEntity get(@Parameter(description = "编号", required = true) @PathVariable String id) {
        return clientService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增客户端信息", description = "新增客户端信息")
    public void save(
            @Parameter(description = "客户端信息", required = true) @Valid @RequestBody ClientInputDTO entity) {
        ClientEntity target = new ClientEntity();
        BeanUtils.copyProperties(entity, target);
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            target.setTenantId(tenantId);
        }
        clientService.save(target);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新客户端信息", description = "更新客户端信息")
    public void update(
            @Parameter(description = "客户端编号", required = true) @PathVariable String id,
            @Parameter(description = "客户端信息", required = true) @RequestBody @Valid ClientInputDTO entity) {
        ClientEntity target = new ClientEntity();
        BeanUtils.copyProperties(entity, target);
        target.setId(id);
        clientService.updateById(target);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户端信息", description = "根据编号删除客户端信息")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ClientEntity client = clientService.getById(id);
        Assert.notNull(client, "客户端不存在");
        clientService.removeById(id);
    }
}
