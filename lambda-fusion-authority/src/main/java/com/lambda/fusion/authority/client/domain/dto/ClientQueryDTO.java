package com.lambda.fusion.authority.client.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "分页查询参数")
public class ClientQueryDTO {

    /**
     * 客户端名称
     */
    private String name;

    /**
     * 主机IP
     */
    private String hosts;

    /**
     * 租户ID
     */
    private String tenantId;
}
