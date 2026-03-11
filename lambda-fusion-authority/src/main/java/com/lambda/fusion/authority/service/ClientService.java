package com.lambda.fusion.authority.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.resource.ApiPermissionTreeNode;
import com.lambda.fusion.authority.model.resource.UserPermission;
import com.lambda.fusion.core.identity.UserDetails;
import java.util.List;

public interface ClientService extends IService<ClientEntity> {

    List<UserPermission> getClientPermissions(List<String> permissionIds);

    List<ApiPermissionTreeNode> listApiPermissions(String clientId, String application, String keyword);

    void bindApiPermission(UserDetails userDetails, String clientId, String permissionId);

    void unbindApiPermission(UserDetails userDetails, String clientId, String permissionId);
}
