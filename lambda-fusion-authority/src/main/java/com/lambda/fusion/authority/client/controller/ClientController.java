package com.lambda.fusion.authority.client.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.authority.client.domain.dto.ClientInputDTO;
import com.lambda.fusion.authority.client.domain.dto.ClientQueryDTO;
import com.lambda.fusion.authority.client.domain.entity.ClientEntity;
import com.lambda.fusion.authority.client.service.ClientService;
import com.lambda.fusion.autoconfig.AuthorityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/clients", "/clients"})
@Tag(name = "认证管理")
public class ClientController {

    private final ClientService clientService;

    @Autowired
    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping({"/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "分页查询所有数据列表",
            description = "分页查询所有数据列表",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20"))
            })
    public Page<ClientEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            ClientQueryDTO clientQueryDTO) {
        String tenantId = OperatorUtils.getOperator().getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            clientQueryDTO.setTenantId(tenantId);
        }
        return clientService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(number, size), clientQueryDTO);
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
        Assert.notNull(client, AuthorityConstants.CLIENT_NOT_FOUND);
        clientService.removeById(id);
        LogContext.setDescription(StrUtil.format("删除客户端 - 名称:{}", client.getName()));
    }
}
