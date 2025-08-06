package com.lambda.fusion.auth.role.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.auth.role.bean.UserRoleDao;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface UserRolesMapper extends BaseMapper<UserRoleDao> {

    /**
     * 根据角色名批量删除用户关系
     * @param authority
     * @param ids
     */
    void batchDelete(String authority, List<String>ids);

}
