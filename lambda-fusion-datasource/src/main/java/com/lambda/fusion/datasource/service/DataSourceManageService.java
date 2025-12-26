package com.lambda.fusion.datasource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import java.util.List;

public interface DataSourceManageService extends IService<DataSourceEntity> {

    List<DataSourceEntity> listAll();

    Page<DataSourceEntity> page(QueryDataSource queryDTO);

    DataSourceEntity get(String id);

    void save(UpsertDataSource input);

    void update(String id, UpsertDataSource input);

    void delete(String id);

    boolean test(String id);

    void enable(String id);

    void disable(String id);
}

