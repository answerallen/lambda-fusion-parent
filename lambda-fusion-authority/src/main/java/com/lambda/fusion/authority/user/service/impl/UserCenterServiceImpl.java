package com.lambda.fusion.authority.user.service.impl;

import cn.hutool.json.JSONUtil;
import com.lambda.cloud.sms.SmsMessageSender;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.user.helper.UserInfoHelper;
import com.lambda.fusion.authority.user.mapper.UserFieldsMapper;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.*;
import com.lambda.fusion.authority.user.service.UserCenterService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
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
    public VerifyCode sendMobileVerifyCode(@NonNull String username, @NonNull String mobile) {

        return null;
    }

    @Override
    public void updateMobile(String username, String mobile, String verifyCode) {
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        User user = userMapper.selectUserByUsername(username);
        if (user == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        if (mobile == null) {
            throw AuthorityBusinessException.invalidParameter("手机号不能为空");
        }
        userMapper.updateMobile(username, mobile);
    }

    @Override
    public void updateEmail(String username, String email, String verifyCode) {
        // 验证参数
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        // 获取用户信息并验证用户是否存在
        User user = userMapper.selectUserByUsername(username);
        if (user == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        if (email == null) {
            throw AuthorityBusinessException.invalidParameter("邮箱不能为空");
        }
        // 更新用户邮箱
        userMapper.updateEmail(username, email);
    }

    @Override
    public User updateInfo(RestUserInfo userInfoDTO) {
        String username = userInfoDTO.getUsername();
        User user = userMapper.selectUserByUsername(username);
        if (user == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        userMapper.updateInfo(username, userInfoDTO.getEmail(), userInfoDTO.getNickname());
        String avatar = userInfoDTO.getAvatar();
        if (StringUtils.isNotEmpty(avatar)) {
            UserInfoEntity userInfo = userInfoMapper.getProps(username);
            // 扩展信息存在，更新头像。扩展信息不存在，插入一条扩展信息
            if (userInfo != null) {
                userInfoMapper.updateAvatar(username, avatar);
            } else {
                userInfo = new UserInfoEntity();
                userInfo.setUsername(username);
                userInfo.setAvatar(avatar);
                userInfoMapper.insert(userInfo);
            }
        }
        if (StringUtils.isNotEmpty(userInfoDTO.getPersonal())) {
            if (StringUtils.isNotBlank(userInfoDTO.getPersonal())) {
                Map<String, Object> tempMap = JSONUtil.parseObj(userInfoDTO.getPersonal());
                List<UserFieldsEntity> fields = UserInfoHelper.buildUserFieldsFromMap(tempMap, username);
                userFieldsMapper.insert(fields);
            }
        }

        return user;
    }
}
