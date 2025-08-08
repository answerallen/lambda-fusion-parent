package com.lambda.fusion.authority.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.role.model.entity.UserRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRolesMapper extends BaseMapper<UserRoleEntity> {

    /**
     * 根据角色名批量删除用户关系
     * @param authority
     * @param ids
     */
    void batchDelete(String authority, List<String> ids);
}
