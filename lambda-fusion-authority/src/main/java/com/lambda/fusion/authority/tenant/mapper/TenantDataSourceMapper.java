package com.lambda.fusion.authority.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.tenant.model.TenantDataSourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantDataSourceMapper extends BaseMapper<TenantDataSourceEntity> {}

