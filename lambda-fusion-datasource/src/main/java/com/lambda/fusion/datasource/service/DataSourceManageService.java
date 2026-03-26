package com.lambda.fusion.datasource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import java.util.List;

public interface DataSourceManageService extends IService<DataSourceEntity> {

    List<DataSourceEntity> listAll();

    Page<DataSourceEntity> page(QueryDataSource queryDTO);

    void save(UpsertDataSource input);

    void update(String id, UpsertDataSource input);

    void delete(String id);

    boolean test(String id);

    void enable(String id);

    void disable(String id);

    TenantDataSourceEntity getTenantDataSource(String tenantId, FusionConstants.DatabaseUsageType usageType);

    List<TenantDataSourceEntity> getTenantDataSources(String tenantId);

    List<TenantDataSourceEntity> listTenantDataSources(List<String> tenantIds);

    void bindTenantDataSource(String tenantId, String datasourceId, FusionConstants.DatabaseUsageType usageType);

    void markTenantDataSourceInitialized(String tenantId);

    void initTenantDataSource(String tenantId);
}
