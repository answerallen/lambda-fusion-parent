package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.model.user.UserPasswordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPasswordMapper extends BaseMapper<UserPasswordEntity> {

    /**
     * 用户更改密码后插入日志
     * @param updatePwdLog 修改密码日志对象
     */
    @InterceptorIgnore(tenantLine = "1")
    void insertLog(UserPasswordEntity updatePwdLog);
}
