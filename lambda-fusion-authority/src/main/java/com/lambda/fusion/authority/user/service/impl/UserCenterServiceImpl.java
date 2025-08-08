package com.lambda.fusion.authority.user.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.sms.SmsMessageSender;
import com.lambda.fusion.authority.user.domain.*;
import com.lambda.fusion.authority.user.domain.entity.UserInfoEntity;
import com.lambda.fusion.authority.user.mapper.UserFieldsMapper;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.service.UserCenterService;
import com.lambda.fusion.autoconfig.AuthorityConstants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
public class UserCenterServiceImpl implements UserCenterService {

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserFieldsMapper userFieldsMapper;
    private final SmsMessageSender shortMessageSender;

    @Override
    public RestVerifyCodeInfo sendMobileVerifyCodeStore(@NonNull String username, @NonNull String mobile) {

        return null;
    }

    @Override
    public void updateMobile(String username, String mobile, String verifyCode) {
        Assert.notNull(username, AuthorityConstants.USER_NAME_NOT_EMPTY);
        MutableUser mutableUser = userMapper.getMutableUserById(username);
        Assert.notNull(mutableUser, AuthorityConstants.USER_NOT_FOUND);
        Assert.notNull(mobile, "lambda.authority.user.mobile.notempty");
        mutableUser.setMobile(mobile);
        userMapper.updateMobile(mutableUser);
    }

    @Override
    public void updateEmail(String username, String email, String verifyCode) {
        // 验证参数
        Assert.notNull(username, AuthorityConstants.USER_NAME_NOT_EMPTY);
        // 获取用户信息并验证用户是否存在
        MutableUser mutableUser = userMapper.getMutableUserById(username);
        Assert.notNull(mutableUser, AuthorityConstants.USER_NOT_FOUND);
        Assert.notNull(email, "lambda.authority.user.email.notempty");
        // 更新用户邮箱
        userMapper.updateEmail(mutableUser);
    }

    @Override
    public MutableUser updateInfo(RestUserInfoParameter restUserInfoParameter) {
        String username = restUserInfoParameter.getUsername();
        MutableUser user = userMapper.getMutableUserById(username);
        Assert.notNull(user, AuthorityConstants.USER_NOT_FOUND);
        userMapper.updateInfo(restUserInfoParameter);
        String avatar = restUserInfoParameter.getAvatar();
        if (StringUtils.isNotEmpty(avatar)) {
            // 获取用户扩展信息
            UserInfoEntity userInfo = userInfoMapper.getProps(username);
            // 扩展信息存在，更新头像。扩展信息不存在，插入一条扩展信息
            if (userInfo != null) {
                // 更新用户扩展信息
                userInfoMapper.updateAvatar(username, avatar);
            } else {
                userInfo = new UserInfoEntity();
                userInfo.setUserid(username);
                userInfo.setAvatar(avatar);
                userInfoMapper.insert(userInfo);
            }
        }
        user.setOnline(true);
        user.setLocked(true);

        if (StringUtils.isNotEmpty(restUserInfoParameter.getPersonal())) {}

        return user;
    }
}
