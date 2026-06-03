package com.lambda.fusion.authority.role.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lambda.fusion.authority.role.model.AuthorityPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问权限数据层接口
 *
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AccessPermissionMapper {

    /**
     * 是否没有任意的权限
     * 用于在移除某一权限时，判断上级节点是否也应该被移除
     *
     */
    boolean noAnyChildrenPermission(AuthorityPermission authorityPermission);

    /**
     * 删除单一的访问权限
     *
     */
    void deletePermission(AuthorityPermission authorityPermission);
}
