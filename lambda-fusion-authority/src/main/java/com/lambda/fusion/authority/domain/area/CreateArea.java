package com.lambda.fusion.authority.domain.area;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建区域请求参数
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = AreaEntity.class)
@Data
public class CreateArea extends BaseDTO<AreaEntity> {

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
