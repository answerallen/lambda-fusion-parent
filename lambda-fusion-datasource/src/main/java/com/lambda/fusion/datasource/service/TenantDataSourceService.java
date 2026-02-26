package com.lambda.fusion.datasource.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.datasource.model.QueryTenantDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import java.util.List;

public interface TenantDataSourceService extends IService<TenantDataSourceEntity> {

    List<TenantDataSourceEntity> listAll();

    TenantDataSourceEntity get(String id);

    void save(UpsertTenantDataSource input);

    void update(String id, UpsertTenantDataSource input);

    void delete(String id);

    IPage<TenantDataSourceEntity> page(QueryTenantDataSource queryDTO);

    boolean test(String id);

    void enable(String id);

    void disable(String id);
}
