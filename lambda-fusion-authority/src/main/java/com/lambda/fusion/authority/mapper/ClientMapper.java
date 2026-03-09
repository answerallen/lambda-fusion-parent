package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.resource.UserPermission;
import com.lambda.security.web.hmac.model.HmacClient;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ClientMapper extends BaseMapper<ClientEntity> {
    /**
     * 根据appid查询客户端信息
     *
     * @param appid
     */

    HmacClient getClientById(String appid);

    /**
     * 批量查询用户权限数据
     *
     * @param permissionIds
     * @return
     */
    List<UserPermission> getClientPermissions(List<String> permissionIds);
}
