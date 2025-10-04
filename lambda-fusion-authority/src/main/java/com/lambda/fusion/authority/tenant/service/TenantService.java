package com.lambda.fusion.authority.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.tenant.model.entity.TenantEntity;
import com.lambda.fusion.authority.tenant.model.vo.TenantOptionVO;
import java.util.List;
import java.util.Map;

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

    /**
     * 查询租户下拉列表
     * @return  List<TenantQuery>
     */
    List<TenantOptionVO> getTenantOptions();

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

    /**
     * Update config.
     *
     * @param operator   the operator
     * @param tenantId   the tenant id
     * @param configMap the config map
     */
    void updateConfig(LoginUser operator, String tenantId, Map<String, Object> configMap);

    /**
     * 获取指定租户的配置信息
     *
     * @param operator       the user
     * @param tenantId the tenant id
     * @return the user tenant configure
     */
    JsonNode getTenantConfigureById(LoginUser operator, String tenantId);

    /**
     * Gets tenant configure map by id.
     *
     * @param tenantId the tenant id
     * @return the tenant configure map by id
     */
    Map<String, Object> getTenantConfigureMapById(String tenantId);

    /**
     * 初始化租户的主库
     * @param tenantId
     * @param operator
     */
    void initTenantMainDataBase(String tenantId, LoginUser operator);
}
