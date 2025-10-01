package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.model.entity.OnlineLogEntity;
import com.lambda.fusion.authority.user.model.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {}
