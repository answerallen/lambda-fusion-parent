package com.lambda.fusion.auth.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.auth.user.domain.UserInfoDO;
import com.lambda.fusion.auth.user.mapper.UserInfoMapper;
import com.lambda.fusion.auth.user.service.UserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoDO> implements UserInfoService {
    @Override
    public UserInfoDO getProps(String id) {
        return baseMapper.getProps(id);
    }

    @Override
    public void unbindUserInfo(LoginUser operator, String type, String username) {}
}
