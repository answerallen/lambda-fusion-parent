package com.lambda.fusion.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantDataSourceMapper extends BaseMapper<TenantDataSourceEntity> {}
