package com.lambda.fusion.auth.tenant.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

/**
 * 租户信息表
 */
@Data
@TableName("la_tenant")
@Schema(description = "租户信息表")
public class TenantEntity {

    /**
     * 租户ID
     */
    @TableId
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
     * 法人
     */
    @Schema(description = "法人")
    @TableField("LEGAL_PERSON")
    private String legalPerson;
    /**
     * 经度
     */
    @Schema(description = "经度")
    @TableField("LONGITUDE")
    private BigDecimal longitude;
    /**
     * 维度
     */
    @Schema(description = "维度")
    @TableField("LATITUDE")
    private BigDecimal latitude;
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
     * 省级编码
     */
    @Schema(description = "省级编码")
    @TableField("PROVINCE")
    private String province;
    /**
     * 市级编码
     */
    @Schema(description = "市级编码")
    @TableField("CITY")
    private String city;
    /**
     * 行政区编码
     */
    @Schema(description = "行政区编码")
    @TableField("DISTRICT")
    private String district;
    /**
     * 是否启用，未启用:0,已启用:1
     */
    @Schema(description = "是否启用，未启用:0,已启用:1, 已停用:99")
    @TableField("ENABLED")
    private Integer enabled;
    /**
     * 创建人
     */
    @Schema(description = "创建人")
    @TableField("CREATE_BY")
    private String createBy;
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @TableField("CREATE_TIME")
    private Date createTime;
    /**
     * 最后修改人
     */
    @Schema(description = "最后修改人")
    @TableField("LAST_UPDATE_BY")
    private String lastUpdateBy;
    /**
     * 最后修改时间
     */
    @Schema(description = "最后修改时间")
    @TableField("LAST_UPDATE_TIME")
    private Date lastUpdateTime;

    /**
     * 审核状态，未审核:0,审核通过:1
     */
    @Schema(description = "审核状态，未审核:0,审核通过:1")
    @TableField("EXAMINE_STATE")
    private Integer examineState;

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
    @Schema(description = "地区")
    @TableField("PREFECTURE")
    private String prefecture;

    /**
     * 地区
     */
    @Schema(description = "地区")
    @TableField(exist = false)
    private List<String> prefectureList;

    /**
     * 租户配置
     */
    @Schema(description = "租户配置")
    @TableField("CONFIG")
    private String config;

    public List<String> getPrefectureList() {
        if (StringUtils.isNotBlank(this.prefecture)) {
            return Arrays.asList(this.prefecture.split(","));
        }
        return Collections.emptyList();
    }
}
