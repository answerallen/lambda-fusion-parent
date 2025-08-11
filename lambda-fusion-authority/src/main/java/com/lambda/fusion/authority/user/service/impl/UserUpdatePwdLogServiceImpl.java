package com.lambda.fusion.authority.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.authority.user.mapper.UserUpdatePwdLogMapper;
import com.lambda.fusion.authority.user.model.entity.UserUpdatePwdLogEntity;
import com.lambda.fusion.authority.user.service.UserUpdatePwdLogService;
import org.springframework.stereotype.Service;

@Service
public class UserUpdatePwdLogServiceImpl extends ServiceImpl<UserUpdatePwdLogMapper, UserUpdatePwdLogEntity>
        implements UserUpdatePwdLogService {}
