package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.model.entity.UserRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    /**
     * 根据角色名批量删除用户关系
     * @param authority
     * @param ids
     */
    default void batchDelete(String authority, List<String> ids) {
        delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getAuthority, authority)
                .in(UserRoleEntity::getUserid, ids));
    }

    default void deleteUserRoles(String userid) {
        delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserid, userid));
    }
}
