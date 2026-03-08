package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ApiResourceMapper {
    List<String> getBoundPermissionIds(@Param("resourceId") String resourceId);

    boolean hasBound(@Param("resourceId") String resourceId, @Param("permissionId") String permissionId);

    void bind(@Param("resourceId") String resourceId, @Param("permissionId") String permissionId);

    void unbind(@Param("resourceId") String resourceId, @Param("permissionId") String permissionId);
}
