package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.model.entity.UserThirdPartEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface UserThirdPartMapper extends BaseMapper<UserThirdPartEntity> {

    default List<UserThirdPartEntity> findByUsername(String username) {
        return selectList(new LambdaQueryWrapper<UserThirdPartEntity>().eq(UserThirdPartEntity::getUsername, username));
    }

    default UserThirdPartEntity findByThirdTypeAndOpenId(String loginType, String openId) {
        return selectOne(new LambdaQueryWrapper<UserThirdPartEntity>()
                .eq(UserThirdPartEntity::getThirdType, loginType)
                .eq(UserThirdPartEntity::getOpenId, openId));
    }

    default String findUsernameByThirdTypeAndOpenId(String loginType, String openId) {
        return Optional.of(selectOne(new LambdaQueryWrapper<UserThirdPartEntity>()
                        .select(UserThirdPartEntity::getUsername)
                        .eq(UserThirdPartEntity::getThirdType, loginType)
                        .eq(UserThirdPartEntity::getOpenId, openId)))
                .map(UserThirdPartEntity::getUsername)
                .orElse(null);
    }

    default int deleteByUsernameAndThirdType(String username, String loginType) {
        return delete(new LambdaQueryWrapper<UserThirdPartEntity>()
                .eq(UserThirdPartEntity::getUsername, username)
                .eq(UserThirdPartEntity::getThirdType, loginType));
    }
}
