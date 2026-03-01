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

    void stopTenant(String tenantId);

    void examineTenant(String tenantId);
}
