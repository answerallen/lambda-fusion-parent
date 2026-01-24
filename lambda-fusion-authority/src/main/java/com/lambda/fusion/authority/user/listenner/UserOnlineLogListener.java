package com.lambda.fusion.authority.user.listenner;

import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserOnlineLogListener extends SaTokenListenerForSimple {

    private final UserOnlineLogService userOnlineLogService;

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        if (loginId != null) {
            userOnlineLogService.online(loginId.toString(),loginParameter.getDeviceType());
        }
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        if (loginId != null) {
            userOnlineLogService.offline(loginId.toString(),null);
        }
    }
}
