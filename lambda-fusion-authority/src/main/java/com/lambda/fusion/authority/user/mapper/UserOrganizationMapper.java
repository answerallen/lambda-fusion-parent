package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.organization.model.UserOrganization;
import com.lambda.fusion.authority.user.model.entity.UserOrganizationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOrganizationMapper extends BaseMapper<UserOrganizationEntity> {

    /**
     * 查询用户关联组织
     *
     * @param userId 用户ID
     * @return 用户组织信息
     */
    UserOrganization queryUserOrganization(String userId);

    /**
     * 用户关联组织
     *
     * @param userOrganization 用户组织信息
     */
    void addUserOrganization(UserOrganization userOrganization);

    /**
     * 更新用户组织
     *
     * @param userOrganization 用户组织信息
     */
    void updateUserOrganization(UserOrganization userOrganization);

    /**
     * 根据组织删除用户组织关系
     *
     * @param orgId 组织编号
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteUserOrganizationByOrg(String orgId);
}
