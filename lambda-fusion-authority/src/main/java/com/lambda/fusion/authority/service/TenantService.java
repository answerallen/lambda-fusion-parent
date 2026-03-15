package com.lambda.fusion.authority.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.model.tenant.TenantEntity;
import com.lambda.fusion.authority.model.tenant.TenantOption;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户信息表
 */
public interface TenantService extends IService<TenantEntity> {

    Page<TenantEntity> pageTenant(Page<TenantEntity> pageable, LambdaQueryWrapper<TenantEntity> queryWrapper);

    List<TenantOption> getTenantOptions();

    TenantEntity createTenantWithLogo(String tenant, MultipartFile logo, String clientName);

    TenantEntity updateTenantWithLogo(String id, String tenant, MultipartFile logo, String clientName);

    void deleteTenant(String tenantId);

    void enableTenant(String tenantId);

    void disableTenant(String tenantId);

    /**
     * 绑定域名
     *
     * @param tenantId 租户ID
     * @param domain   域名
     */
    void bindDomain(String tenantId, String domain);

    /**
     * 解绑域名
     *
     * @param tenantId 租户ID
     */
    void unbindDomain(String tenantId);

    /**
     * 根据域名解析租户
     *
     * @param domain 域名
     * @return 租户实体
     */
    TenantEntity resolveByDomain(String domain);
}
