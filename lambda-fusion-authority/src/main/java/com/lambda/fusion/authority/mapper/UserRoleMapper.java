package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.user.UserRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    default void batchDelete(String authority, List<String> ids) {
        delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getAuthority, authority)
                .in(UserRoleEntity::getUsername, ids));
    }

    default void deleteUserRoles(String userid) {
        delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUsername, userid));
    }
}
