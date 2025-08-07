package com.lambda.fusion.authority.tenant.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 租户信息表
 */
@Data
@Schema(description = "租户信息表")
public class TenantVO {

    /**
     * 租户编码
     */
    @Schema(description = "租户编码", required = true)
    private String tenantCode;

    /**
     * 租户名称
     */
    @Schema(description = "租户名称", required = true)
    private String tenantName;
    /**
     * 租户地址
     */
    @Schema(description = "租户地址")
    private String tenantAddress;
    /**
     * 网站地址
     */
    @Schema(description = "网站地址")
    private String tenantWebsite;
    /**
     * 租户描述
     */
    @Schema(description = "租户描述")
    private String tenantDesc;
    /**
     * LOGO地址
     */
    @Schema(description = "LOGO地址")
    private String tenantLogo;
    /**
     * 联系人
     */
    @Schema(description = "联系人")
    private String liaisonMan;
    /**
     * 联系电话
     */
    @Schema(description = "联系电话", required = true)
    private String liaisonPhone;
    /**
     * 法人
     */
    @Schema(description = "法人", required = true)
    private String legalPerson;
    /**
     * 经度
     */
    @Schema(description = "经度")
    private BigDecimal longitude;
    /**
     * 维度
     */
    @Schema(description = "维度")
    private BigDecimal latitude;
    /**
     * ICP备案号
     */
    @Schema(description = "ICP备案号")
    private String icpPutOnRecord;
    /**
     * 联网备案号
     */
    @Schema(description = "联网备案号")
    private String networkPutOnRecord;
    /**
     * 省级编码
     */
    @Schema(description = "省级编码")
    private String province;
    /**
     * 市级编码
     */
    @Schema(description = "市级编码")
    private String city;
    /**
     * 行政区编码
     */
    @Schema(description = "行政区编码")
    private String district;
    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createBy;
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Date createTime;
    /**
     * 最后修改人
     */
    @Schema(description = "最后修改人")
    private String lastUpdateBy;
    /**
     * 最后修改时间
     */
    @Schema(description = "最后修改时间")
    private Date lastUpdateTime;

    /**
     * 拥有者
     */
    @Schema(description = "拥有者")
    private String owner;

    /**
     * 别名
     */
    @Schema(description = "别名")
    private String alias;

    /**
     * 地区
     */
    @Schema(description = "地区")
    private String prefecture;

    /**
     * 地区
     */
    @Schema(description = "地区")
    private List<String> prefectureList;
}
