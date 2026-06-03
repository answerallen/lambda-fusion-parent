package com.lambda.fusion.authority.client.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.core.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/**
 * 客户端分页查询DTO
 *
 * <p>继承PageQuery基类，提供统一的分页查询功能，支持按客户端名称、主机IP等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持客户端名称模糊查询</li>
 * <li>支持主机IP模糊查询</li>
 * <li>自动处理租户隔离</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 *
 */
@Getter
@Setter
@Schema(description = "客户端分页查询参数")
public class ClientQuery extends Pagination<ClientEntity> {

    /**
     * 客户端名称
     */
    @Schema(description = "客户端名称，支持模糊查询")
    @Size(max = 100, message = "客户端名称长度不能超过100个字符")
    private String name;

    /**
     * 主机IP
     */
    @Schema(description = "主机IP地址，支持模糊查询")
    @Size(max = 500, message = "主机IP长度不能超过500个字符")
    private String hosts;

    /**
     * 租户ID
     */
    @Schema(description = "租户ID，用于多租户数据隔离")
    @Size(max = 64, message = "租户ID长度不能超过64个字符")
    private String tenantId;

    @Override
    public LambdaQueryWrapper<ClientEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ClientEntity> lambdaQueryWrapper = super.getLambdaQueryWrapper();
        lambdaQueryWrapper.like(StringUtils.isNotBlank(name), ClientEntity::getName, name);
        lambdaQueryWrapper.like(StringUtils.isNotBlank(hosts), ClientEntity::getHosts, hosts);
        lambdaQueryWrapper.eq(StringUtils.isNotBlank(tenantId), ClientEntity::getTenantId, tenantId);
        return lambdaQueryWrapper;
    }
}
