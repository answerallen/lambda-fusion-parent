package com.lambda.fusion.authority.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.model.client.ClientEntity;
import com.lambda.fusion.authority.model.resource.UserPermission;
import java.util.List;

public interface ClientService extends IService<ClientEntity> {
    /**
     * 批量查询用户权限数据
     *
     * @param permissionIds
     * @return
     */
    List<UserPermission> getClientPermissions(List<String> permissionIds);
}
