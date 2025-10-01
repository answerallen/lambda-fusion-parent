package com.lambda.fusion.authority.user.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.sms.SmsMessageSender;
import com.lambda.fusion.authority.user.mapper.UserFieldsMapper;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import com.lambda.fusion.authority.user.model.RestUserInfoParameter;
import com.lambda.fusion.authority.user.model.RestVerifyCodeInfo;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.authority.user.service.UserCenterService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
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
        Assert.notNull(username, "username must not be empty");
        MutableUserVO mutableUser = userMapper.getMutableUserById(username);
        Assert.notNull(mutableUser, "user not found");
        Assert.notNull(mobile, "mobile must not be empty");
        mutableUser.setMobile(mobile);
        userMapper.updateMobile(mutableUser);
    }

    @Override
    public void updateEmail(String username, String email, String verifyCode) {
        // 验证参数
        Assert.notNull(username, "username must not be empty");
        // 获取用户信息并验证用户是否存在
        MutableUserVO mutableUser = userMapper.getMutableUserById(username);
        Assert.notNull(mutableUser, "user not found");
        Assert.notNull(email, "lambda.authority.user.email.notempty");
        // 更新用户邮箱
        userMapper.updateEmail(mutableUser);
    }

    @Override
    public MutableUserVO updateInfo(RestUserInfoParameter restUserInfoParameter) {
        String username = restUserInfoParameter.getUsername();
        MutableUserVO user = userMapper.getMutableUserById(username);
        Assert.notNull(user, "user not found");
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

        if (StringUtils.isNotEmpty(restUserInfoParameter.getPersonal())) {
            // TODO 更新用户扩展信息
            System.out.println(restUserInfoParameter.getPersonal());
        }

        return user;
    }
}
