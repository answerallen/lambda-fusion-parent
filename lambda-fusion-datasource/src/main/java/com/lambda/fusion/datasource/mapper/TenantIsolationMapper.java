package com.lambda.fusion.datasource.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantIsolationMapper {
    @Select("SELECT isolation_mode FROM LA_TENANT WHERE TENANT_ID = #{tenantId}")
    Integer selectIsolationModeCode(@Param("tenantId") String tenantId);
}
