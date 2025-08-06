package com.lambda.fusion.auth.client.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.auth.client.domain.entity.ClientEntity;
import com.lambda.fusion.auth.client.domain.dto.Parameters;
import com.lambda.fusion.auth.resource.bean.UserPermission;

import java.util.List;

public interface ClientService extends IService<ClientEntity> {

    /***
     * 分页查询
     * @param pageable  分页信息
     * @param parameters 查询参数
     */
    Page<ClientEntity> page(Page<ClientEntity> pageable, Parameters parameters);

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

