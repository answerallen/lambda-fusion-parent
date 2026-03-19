package com.lambda.fusion.authority.service;

import com.lambda.fusion.authority.model.resource.ApiPermissionTreeNode;
import java.util.List;

public interface PermissionService {
    List<ApiPermissionTreeNode> listPermissionTree(String resourceId, String application, String keyword);

    void bind(String resourceId, String permissionId);

    void unbind(String resourceId, String permissionId);
}
