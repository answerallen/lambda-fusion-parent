package com.lambda.fusion.authority.tenant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.tenant.model.TenantDataSourceEntity;
import com.lambda.fusion.authority.tenant.model.UpsertTenantDataSource;

import java.util.List;

public interface TenantDataSourceManageService extends IService<TenantDataSourceEntity> {

    List<TenantDataSourceEntity> listAll();

    TenantDataSourceEntity get(String id);

    void save(UpsertTenantDataSource input);

    void update(String id, UpsertTenantDataSource input);

    void delete(String id);
}

