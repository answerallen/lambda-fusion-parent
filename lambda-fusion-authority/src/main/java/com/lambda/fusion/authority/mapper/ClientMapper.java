package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.client.AuthResource;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.resource.UserPermission;
import com.lambda.security.web.hmac.model.HmacClient;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClientMapper extends BaseMapper<ClientEntity> {
    /**
     * 获取已经拥有的资源编号
     *
     * @param clientId
     * @return java.util.List<java.lang.String>
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> hasAuthorized(@Param("clientId") String clientId);

    /**
     * 批量保存客户端的接口授权
     *
     * @param clientId
     * @param ids
     */
    @InterceptorIgnore(tenantLine = "true")
    void batchSaveAuthResources(@Param("clientId") String clientId, @Param("ids") List<String> ids);

    /**
     * 批量删除客户端的接口授权
     *
     * @param clientId
     * @param ids
     */
    @InterceptorIgnore(tenantLine = "true")
    void batchDeleteAuthResources(@Param("clientId") String clientId, @Param("ids") List<String> ids);

    /**
     * 查询角色权限
     *
     * @param parameters
     */
    @InterceptorIgnore(tenantLine = "true")
    List<AuthResource> getAuthorization(@Param("parameters") Map<String, Object> parameters);

    /**
     * 根据appid查询客户端信息
     *
     * @param appid
     */
    @InterceptorIgnore(tenantLine = "true")
    HmacClient getClientById(String appid);

    /**
     * 根据资源ID查询有该权限的用户
     *
     * @param rid
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> getUsersByRid(String rid);

    /**
     * 批量查询用户权限数据
     *
     * @param permissionIds
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<UserPermission> getUserPermissions(List<String> permissionIds);
}
