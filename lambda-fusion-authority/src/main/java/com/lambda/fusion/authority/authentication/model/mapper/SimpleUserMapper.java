package com.lambda.fusion.authority.authentication.model.mapper;

import com.lambda.fusion.authority.authentication.model.domain.SimpleUser;
import com.lambda.fusion.core.user.User;
import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

/**
 * SimpleUser与User对象映射器
 * 使用MapStruct进行对象转换，提供类型安全和高性能的映射
 */
@Mapper
public interface SimpleUserMapper {

    /**
     * 映射器实例
     */
    SimpleUserMapper INSTANCE = Mappers.getMapper(SimpleUserMapper.class);

    /**
     * 将SimpleUser转换为User对象
     *
     * @param simpleUser 简单用户对象
     * @return User对象
     */
    @Mapping(target = "accountLocked", source = "enabled")
    @Mapping(target = "accountExpired", source = "expiredTime", qualifiedByName = "mapAccountExpired")
    @Mapping(target = "roles", source = "authorities")
    @Mapping(target = "dev", ignore = true)
    User toUser(SimpleUser simpleUser);

    /**
     * 映射账户过期状态
     * 根据过期时间判断账户是否过期
     *
     * @param expiredTime 过期时间
     * @return 账户是否过期
     */
    @Named("mapAccountExpired")
    default Boolean mapAccountExpired(LocalDateTime expiredTime) {
        if (expiredTime == null) {
            return false;
        }
        return expiredTime.isAfter(LocalDateTime.now());
    }
}
