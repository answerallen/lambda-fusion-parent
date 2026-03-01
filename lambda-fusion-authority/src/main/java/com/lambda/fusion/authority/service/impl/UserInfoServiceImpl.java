package com.lambda.fusion.authority.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.model.user.UserInfoEntity;
import com.lambda.fusion.authority.mapper.UserInfoMapper;
import com.lambda.fusion.authority.service.UserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoEntity> implements UserInfoService {
    @Override
    public UserInfoEntity getProps(String id) {
        return baseMapper.getProps(id);
    }

    @Override
    public void unbindUserInfo(LoginUser operator, String type, String username) {}
}
