package com.lambda.fusion.authority.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.tenant.model.Tenant;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户信息表
 */
public interface TenantService extends IService<TenantEntity> {

    /***
     * 分页查询（使用LambdaQueryWrapper）
     * @param pageable  分页信息
     * @param queryWrapper 查询条件
     */
    Page<TenantEntity> page(Page<TenantEntity> pageable, LambdaQueryWrapper<TenantEntity> queryWrapper);

    Page<TenantEntity> pageTenant(TenantQuery queryDTO);

    /**
     * 查询租户下拉列表
     * @return  List<TenantQuery>
     */
    List<TenantOption> getTenantOptions();


    TenantEntity createTenantWithLogo(String tenant, MultipartFile logo, String clientName);

    TenantEntity updateTenantWithLogo(String id, String tenant, MultipartFile logo, String clientName);

    void deleteTenant(String tenantId);

    void enableTenant(String tenantId);

    void disableTenant(String tenantId);

    void stopTenant(String tenantId);

    void examineTenant(String tenantId);

    /**
     * 禁用/启用租户信息
     * @param operator  当前用户
     * @param enabled   是否启用
     * @param tenantId        租户ID
     */
    void prohibitTenant(LoginUser operator, Integer enabled, String tenantId);

    /**
     * 审核租户信息
     * @param operator  当前用户
     * @param enabled   是否审核通过
     * @param tenantId        租户ID
     */
    void examineTenant(LoginUser operator, Integer enabled, String tenantId);

    /**
     * 删除租户信息
     * @param operator  当前用户
     * @param tenantId  租户ID
     */
    void deleteTenant(LoginUser operator, String tenantId);
}
