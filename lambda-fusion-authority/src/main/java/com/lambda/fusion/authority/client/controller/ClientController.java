package com.lambda.fusion.authority.client.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.logger.context.LogContext;
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
@RequestMapping({"/clients", "/clients"})
@Tag(name = "认证管理")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    @PostMapping("/page")
    @Operation(
            summary = "分页查询客户端列表（推荐）",
            description = "基于PageQuery基类的分页查询，支持更丰富的查询条件和统一的分页处理")
    public Page<ClientEntity> page(@Valid @RequestBody ClientPageQueryDTO queryDTO) {

        // 租户隔离处理
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            queryDTO.setTenantId(tenantId);
        }

        // 使用PageQuery基类的分页对象
        Page<ClientEntity> page = queryDTO.getPage();

        // 执行分页查询
        return clientService.page(page, queryDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询客户端信息", description = "根据id查询客户端信息")
    public ClientEntity get(@Parameter(description = "编号", required = true) @PathVariable String id) {
        return clientService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增客户端信息", description = "新增客户端信息")
    public ClientEntity save(
            @Parameter(description = "客户端信息", required = true) @Valid @RequestBody ClientInputDTO entity) {
        ClientEntity target = new ClientEntity();
        BeanUtils.copyProperties(entity, target);
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            target.setTenantId(tenantId);
        }
        clientService.save(target);
        return clientService.getById(target.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新客户端信息", description = "更新客户端信息")
    public ClientEntity update(
            @Parameter(description = "客户端编号", required = true) @PathVariable String id,
            @Parameter(description = "客户端信息", required = true) @RequestBody @Valid ClientInputDTO entity) {
        ClientEntity target = new ClientEntity();
        BeanUtils.copyProperties(entity, target);
        target.setId(id);
        clientService.updateById(target);
        return clientService.getById(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户端信息", description = "根据编号删除客户端信息")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ClientEntity client = clientService.getById(id);
        Assert.notNull(client, "客户端不存在");
        clientService.removeById(id);
        LogContext.setDescription(StrUtil.format("删除客户端 - 名称:{}", client.getName()));
    }
}
