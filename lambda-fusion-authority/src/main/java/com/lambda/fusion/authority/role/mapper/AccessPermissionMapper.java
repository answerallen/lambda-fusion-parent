package com.lambda.fusion.authority.role.mapper;

import com.lambda.fusion.authority.role.bean.AccessPermissionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问权限数据层接口
 *
 */
@Mapper
public interface AccessPermissionMapper {

    /**
     * 是否没有任意的权限
     * 用于在移除某一权限时，判断上级节点是否也应该被移除
     *
     * @param parameters
     * @return boolean
     */
    boolean noAnyChildrenPermission(AccessPermissionDO parameters);

    /**
     * 删除单一的访问权限
     *
     * @param parameters
     * @return void
     */
    void deletePermission(AccessPermissionDO parameters);
}
