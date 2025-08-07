package com.lambda.fusion.authority.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.user.domain.UserInfoDO;

public interface UserInfoService extends IService<UserInfoDO> {

    /**
     * 获取用户附加信息
     * @param id 用户id
     * @return 用户附加信息
     */
    UserInfoDO getProps(String id);

    /**
     * 解除第三方绑定信息
     * @param operator  当前登录用户信息
     * @param type      第三方绑定类型(1、钉钉；2、微信)
     * @param username  用户名
     */
    void unbindUserInfo(LoginUser operator, String type, String username);
}
