package com.lambda.fusion.authority.model.area;

import lombok.Data;

/**
 * 行政区划业务模型
 */
@Data
public class Area {

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
    private String areaCode;

    /**
     * 区域名称
     */
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
     * 类型（省、市、区、街道等）
     */
    private String type;
}
