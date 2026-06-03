package com.lambda.fusion.authority.tenant.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户域名解析信息（公开DTO，仅包含展示字段）
 */
@Data
@Schema(description = "租户域名解析信息")
public class TenantDomainInfo {

    @Schema(description = "租户编码")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "LOGO地址")
    private String tenantLogo;

    @Schema(description = "绑定域名")
    private String tenantDomain;

    @Schema(description = "网站地址")
    private String tenantWebsite;

    @Schema(description = "ICP备案号")
    private String icpPutOnRecord;

    @Schema(description = "联网备案号")
    private String networkPutOnRecord;

    /**
     * 从 TenantEntity 转换
     */
    public static TenantDomainInfo fromEntity(TenantEntity entity) {
        if (entity == null) {
            return null;
        }
        TenantDomainInfo info = new TenantDomainInfo();
        info.setTenantCode(entity.getTenantCode());
        info.setTenantName(entity.getTenantName());
        info.setTenantLogo(entity.getTenantLogo());
        info.setTenantDomain(entity.getTenantDomain());
        info.setTenantWebsite(entity.getTenantWebsite());
        info.setIcpPutOnRecord(entity.getIcpPutOnRecord());
        info.setNetworkPutOnRecord(entity.getNetworkPutOnRecord());
        return info;
    }
}
