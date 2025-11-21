package com.lambda.fusion.authority.organization.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.organization.domain.UserOrganizationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOrganizationMapper extends BaseMapper<UserOrganizationEntity> {

    /**
     * 查询用户关联组织
     *
     * @param userId 用户ID
     * @return 用户组织信息
     */
    default UserOrganizationEntity queryUserOrganization(String userId) {
        return selectOne(
                new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getUserid, userId));
    }

    /**
     * 根据组织删除用户组织关系
     *
     * @param orgId 组织编号
     */
    @InterceptorIgnore(tenantLine = "true")
    default void deleteUserOrganizationByOrg(String orgId) {
        delete(new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getOrganizationId, orgId));
    }

    default void deleteUserOrganizationByUser(String username) {
        delete(new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getUserid, username));
    }
}
