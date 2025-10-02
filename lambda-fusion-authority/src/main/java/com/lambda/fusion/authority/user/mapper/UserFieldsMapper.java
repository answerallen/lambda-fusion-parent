package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.model.entity.UserFieldsEntity;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人中心扩展字段
 */
@Mapper
public interface UserFieldsMapper extends BaseMapper<UserFieldsEntity> {

    /**
     * 获取选择用户的扩展信息
     * @param ids 用户id
     */
    List<UserFieldsEntity> getPersonUser(Set<String> ids);

    /**
     * 查询单个用户的扩展字段信息
     * @param username 用户名称
     * @return List<UserFields>
     */
    List<UserFieldsEntity> getListByUsername(String username);

    /**
     * 根据用户名删除用户扩展字段信息
     *
     * @param username 用户名称
     */
    default void deleteByUsername(String username) {
        delete(new LambdaQueryWrapper<UserFieldsEntity>().eq(UserFieldsEntity::getUsername, username));
    }
}
