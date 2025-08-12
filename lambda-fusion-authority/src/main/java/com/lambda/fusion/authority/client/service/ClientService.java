package com.lambda.fusion.authority.client.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.client.model.entity.ClientEntity;
import com.lambda.fusion.authority.resource.model.UserPermission;

import java.util.List;

public interface ClientService extends IService<ClientEntity> {

    /**
     * 根据资源编号获取有权限的用户列表
     *
     * @param rid
     * @return java.util.List<java.lang.String>
     */
    List<String> getUsersByResourceId(String rid);

    /**
     * 批量查询用户权限数据
     *
     * @param permissionIds
     * @return
     */
    List<UserPermission> getUserPermissions(List<String> permissionIds);
}
