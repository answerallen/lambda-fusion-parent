package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.organization.UserOrganizationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface UserOrganizationMapper extends BaseMapper<UserOrganizationEntity> {

    default UserOrganizationEntity selectUserOrganization(String username) {
        return selectOne(
                new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getUsername, username));
    }
    
    default void deleteUserOrganizationByOrg(String orgId) {
        delete(new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getOrganizationId, orgId));
    }

    default void deleteUserOrganizationByUser(String username) {
        delete(new LambdaQueryWrapper<UserOrganizationEntity>().eq(UserOrganizationEntity::getUsername, username));
    }
}
