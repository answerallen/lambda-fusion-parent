package com.lambda.fusion.authority.tenant.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租户信息表
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    /**
     * 禁用/启用租户
     * @param enabled   启用状态
     * @param id  租户编号
     */
    void prohibitTenantByTenantId(@Param("enabled") Integer enabled, @Param("id") String id);

    /**
     * 审核租户信息
     * @param enabled   审核状态
     * @param id  租户编号
     */
    void examineTenantByTenantId(@Param("enabled") Integer enabled, @Param("id") String id);

    /**
     * 查询租户信息列表
     */
    List<TenantQuery> queryTenantList();

    /**
     * Is exist boolean.
     *
     * @param id the id
     * @return the boolean
     */
    boolean isExist(String id);

    /**
     * Update config.
     *
     * @param id         the id
     * @param configJson the config json
     */
    void updateConfig(@Param("id") String id, @Param("configJson") String configJson);

    /**
     * Gets config json.
     *
     * @param id the id
     * @return the config json
     */
    String getConfigJson(@Param("id") String id);
}
