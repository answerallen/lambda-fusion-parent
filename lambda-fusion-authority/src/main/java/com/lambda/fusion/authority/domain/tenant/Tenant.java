package com.lambda.fusion.authority.domain.tenant;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户信息表
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = TenantEntity.class)
@Data
@Schema(description = "租户信息表")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class Tenant extends BaseDTO<TenantEntity> {

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
    @Schema(description = "联系电话")
    private String liaisonPhone;
    /**
     * 机构名称
     */
    @Schema(description = "机构名称")
    private String enterpriseName;

    /**
     * 机构编码
     */
    @Schema(description = "机构编码")
    private String enterpriseCode;
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
     * 创建人
     */
    @Schema(description = "创建人")
    private String createdBy;
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Date createdAt;
    /**
     * 最后修改人
     */
    @Schema(description = "最后修改人")
    private String updatedBy;
    /**
     * 最后修改时间
     */
    @Schema(description = "最后修改时间")
    private Date updatedAt;

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
    @Schema(description = "隔离模式")
    @TableField("isolation_mode")
    private FusionConstants.IsolationMode isolationMode;

    /**
     * 地区
     */
    @Schema(description = "区域编码")
    private String areaCode;
}
