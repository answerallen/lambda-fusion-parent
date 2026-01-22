package com.lambda.fusion.authority.area.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新区域请求参数
 */
@Data
public class UpdateArea {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 上级区域编码
     */
    private String parentCode;

    /**
     * 区域编码
     */
    @NotBlank(message = "区域编码不能为空")
    private String areaCode;

    /**
     * 区域名称
     */
    @NotBlank(message = "区域名称不能为空")
    private String areaName;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 深度
     */
    private Integer depth;

    /**
     * 类型
     */
    private String type;
}
