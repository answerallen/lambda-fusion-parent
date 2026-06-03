package com.lambda.fusion.authority.tenant.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户信息表
 */
@Getter
@Setter
@Schema(description = "租户信息表分页查询参数")
public class TenantOption {

    /**
     * 租户编码
     */
    @Schema(description = "租户编码")
    private String tenantCode;
    /**
     * 租户名称
     */
    @Schema(description = "租户名称")
    private String tenantName;
}
