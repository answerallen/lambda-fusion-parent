package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.tenant.TenantEntity;
import com.lambda.fusion.authority.model.tenant.TenantOption;
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
     * @param status   状态
     * @param id  租户编号
     */
    void prohibitTenantByTenantId(@Param("status") Integer status, @Param("id") String id);

    /**
     * 查询租户信息列表
     */
    List<TenantOption> queryTenantList();

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

    /**
     * 根据域名查询租户
     *
     * @param domain 域名
     * @return 租户实体
     */
    TenantEntity selectByDomain(@Param("domain") String domain);

    /**
     * 检查域名是否已被绑定（排除指定租户）
     *
     * @param domain          域名
     * @param excludeTenantId 排除的租户ID
     * @return 是否已被绑定
     */
    boolean isDomainBound(@Param("domain") String domain, @Param("excludeTenantId") String excludeTenantId);
}
