package com.lambda.fusion.authority.model.area;

import lombok.Data;

/**
 * 行政区划查询参数
 */
@Data
public class AreaQuery {

    /**
     * 上级区域编码
     */
    private String parentCode;

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

    /**
     * 关键字（名称模糊搜索）
     */
    private String keyword;
}
