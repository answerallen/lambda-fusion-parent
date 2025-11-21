package com.lambda.fusion.authority.user.service.impl;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.sms.SmsMessageSender;
import com.lambda.fusion.authority.user.mapper.UserFieldsMapper;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.RestUserInfo;
import com.lambda.fusion.authority.user.model.UserInfoEntity;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.VerifyCode;
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
    public VerifyCode sendMobileVerifyCodeStore(@NonNull String username, @NonNull String mobile) {

        return null;
    }

    @Override
    public void updateMobile(String username, String mobile, String verifyCode) {
        Assert.notNull(username, "username must not be empty");
        Assert.isFalse(userMapper.hasExists(username), "user not found");
        Assert.notNull(mobile, "mobile must not be empty");
        userMapper.updateMobile(username, mobile);
    }

    @Override
    public void updateEmail(String username, String email, String verifyCode) {
        // 验证参数
        Assert.notNull(username, "username must not be empty");
        // 获取用户信息并验证用户是否存在
        User user = userMapper.getMutableUserById(username);
        Assert.notNull(user, "user not found");
        Assert.notNull(email, "email not found");
        // 更新用户邮箱
        userMapper.updateEmail(username, email);
    }

    @Override
    public User updateInfo(RestUserInfo userInfoDTO) {
        String username = userInfoDTO.getUsername();
        User user = userMapper.getMutableUserById(username);
        Assert.notNull(user, "user not found");
        userMapper.updateInfo(username, userInfoDTO.getEmail(), userInfoDTO.getNickname());
        String avatar = userInfoDTO.getAvatar();
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

        if (StringUtils.isNotEmpty(userInfoDTO.getPersonal())) {
            // TODO 更新用户扩展信息
            System.out.println(userInfoDTO.getPersonal());
        }

        return user;
    }
}
