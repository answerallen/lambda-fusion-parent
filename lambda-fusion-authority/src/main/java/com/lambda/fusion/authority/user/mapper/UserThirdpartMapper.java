package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.model.entity.UserThirdpartEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface UserThirdpartMapper extends BaseMapper<UserThirdpartEntity> {

    default List<UserThirdpartEntity> findByUsername(String username) {
        return selectList(new LambdaQueryWrapper<UserThirdpartEntity>().eq(UserThirdpartEntity::getUsername, username));
    }

    default UserThirdpartEntity findByLoginTypeAndOpenId(String loginType, String openId) {
        return selectOne(new LambdaQueryWrapper<UserThirdpartEntity>()
                .eq(UserThirdpartEntity::getLoginType, loginType)
                .eq(UserThirdpartEntity::getOpenId, openId));
    }

    default String findUsernameByLoginTypeAndOpenId(String loginType, String openId) {
        return Optional.of(selectOne(new LambdaQueryWrapper<UserThirdpartEntity>()
                        .select(UserThirdpartEntity::getUsername)
                        .eq(UserThirdpartEntity::getLoginType, loginType)
                        .eq(UserThirdpartEntity::getOpenId, openId)))
                .map(UserThirdpartEntity::getUsername).orElse(null);

    }

    default boolean existsByLoginTypeAndOpenId(String loginType, String openId) {
        return exists(new LambdaQueryWrapper<UserThirdpartEntity>()
                .eq(UserThirdpartEntity::getLoginType, loginType)
                .eq(UserThirdpartEntity::getOpenId, openId));
    }

    default int deleteByUsernameAndLoginType(String username, String loginType) {
        return delete(new LambdaQueryWrapper<UserThirdpartEntity>()
                .eq(UserThirdpartEntity::getUsername, username)
                .eq(UserThirdpartEntity::getLoginType, loginType));
    }

}
