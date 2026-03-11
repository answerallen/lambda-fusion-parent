package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.resource.UserPermission;
import com.lambda.security.web.hmac.model.HmacClient;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ClientMapper extends BaseMapper<ClientEntity> {

    HmacClient getClientById(String appid);

    List<UserPermission> getClientPermissions(List<String> permissionIds);

    List<String> getBoundPermissionIds(@Param("clientId") String clientId);

    boolean hasBound(@Param("clientId") String clientId, @Param("permissionId") String permissionId);

    void bind(@Param("clientId") String clientId, @Param("permissionId") String permissionId);

    void unbind(@Param("clientId") String clientId, @Param("permissionId") String permissionId);
}
