package com.lambda.fusion.authority.model.tenant;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户信息表
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("la_tenant")
@Schema(description = "租户信息表")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class TenantEntity extends BaseEntity {

    /**
     * 租户ID
     */
    @TableId(value = "TENANT_ID")
    @Schema(description = "租户ID")
    @TableField("TENANT_ID")
    private String tenantId;
    /**
     * 租户编码
     */
    @Schema(description = "租户编码")
    @TableField("TENANT_CODE")
    private String tenantCode;
    /**
     * 租户名称
     */
    @Schema(description = "租户名称")
    @TableField("TENANT_NAME")
    private String tenantName;
    /**
     * 租户地址
     */
    @Schema(description = "租户地址")
    @TableField("TENANT_ADDRESS")
    private String tenantAddress;
    /**
     * 网站地址
     */
    @Schema(description = "网站地址")
    @TableField("TENANT_WEBSITE")
    private String tenantWebsite;
    /**
     * 租户描述
     */
    @Schema(description = "租户描述")
    @TableField("TENANT_DESC")
    private String tenantDesc;
    /**
     * LOGO地址
     */
    @Schema(description = "LOGO地址")
    @TableField("TENANT_LOGO")
    private String tenantLogo;
    /**
     * 联系人
     */
    @Schema(description = "联系人")
    @TableField("LIAISON_MAN")
    private String liaisonMan;
    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    @TableField("LIAISON_PHONE")
    private String liaisonPhone;
    /**
     * 机构名称
     */
    @Schema(description = "机构名称")
    @TableField("enterprise_name")
    private String enterpriseName;

    /**
     * 机构编码
     */
    @Schema(description = "机构编码")
    @TableField("enterprise_code")
    private String enterpriseCode;

    /**
     * ICP备案号
     */
    @Schema(description = "ICP备案号")
    @TableField("ICP_PUT_ON_RECORD")
    private String icpPutOnRecord;
    /**
     * 联网备案号
     */
    @Schema(description = "联网备案号")
    @TableField("NETWORK_PUT_ON_RECORD")
    private String networkPutOnRecord;
    /**
     * 是否启用，未启用:0,已启用:1
     */
    @Schema(description = "状态，禁用:0,启用:1")
    @TableField("ENABLED")
    private Integer status;

    /**
     * 拥有者
     */
    @Schema(description = "拥有者")
    @TableField("OWNER")
    private String owner;

    /**
     * 别名
     */
    @Schema(description = "别名")
    @TableField("ALIAS")
    private String alias;

    /**
     * 地区
     */
    @Schema(description = "区域编码")
    @TableField("area_code")
    private String areaCode;

    /**
     * 隔离模式
     */
    @Schema(description = "隔离模式")
    @TableField("isolation_mode")
    private FusionConstants.IsolationMode isolationMode;

    /**
     * 租户配置
     */
    @Schema(description = "租户配置")
    @TableField("CONFIG")
    private String config;
}
