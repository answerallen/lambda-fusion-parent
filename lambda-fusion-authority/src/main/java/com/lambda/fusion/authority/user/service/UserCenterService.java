package com.lambda.fusion.authority.user.service;

import com.lambda.fusion.authority.user.model.RestUserInfo;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.VerifyCode;
import org.jspecify.annotations.NonNull;

public interface UserCenterService {
    /**
     * 发送手机验证码
     *
     * @param username 用户名
     * @param mobile 手机号
     */
    VerifyCode sendMobileVerifyCode(@NonNull String username, @NonNull String mobile);

    /**
     * 更新用户手机号
     *
     * @param username   用户编号
     * @param mobile     新手机号
     * @param verifyCode 短信验证码
     */
    void updateMobile(String username, String mobile, String verifyCode);

    /**
     * 更新用户邮箱
     *
     * @param username   用户编号
     * @param email      新邮箱
     * @param verifyCode 邮箱验证码
     */
    void updateEmail(String username, String email, String verifyCode);

    /**
     * 更新用户信息
     *
     * @param restUserInfoParameter 用户信息
     */
    User updateInfo(RestUserInfo restUserInfoParameter);
}
