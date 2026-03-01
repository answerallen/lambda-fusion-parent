package com.lambda.fusion.authority.model.area;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 行政区划实体类
 */
@Data
@TableName("la_area")
public class AreaEntity {

    /**
     * 主键ID
     */
    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 上级区域编码
     */
    @TableField("PARENT_CODE")
    private String parentCode;

    /**
     * 区域编码
     */
    @TableField("AREA_CODE")
    private String areaCode;

    /**
     * 区域名称
     */
    @TableField("AREA_NAME")
    private String areaName;

    /**
     * 层级
     */
    @TableField("LEVEL")
    private Integer level;

    /**
     * 深度
     */
    @TableField("DEPTH")
    private Integer depth;

    /**
     * 类型（省、市、区、街道等）
     */
    @TableField("TYPE")
    private String type;
}
