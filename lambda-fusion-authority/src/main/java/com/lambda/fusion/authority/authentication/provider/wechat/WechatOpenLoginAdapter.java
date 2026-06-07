package com.lambda.fusion.authority.authentication.provider.wechat;

import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;

public class WechatOpenLoginAdapter {

    private final AuthWechatMiniProgramRequest authRequest;

    public WechatOpenLoginAdapter(AuthorityProperties.ThirdPartConfig partConfig) {
        AuthConfig config = AuthConfig.builder()
                .clientId(partConfig.getWxOpen().getAppId())
                .clientSecret(partConfig.getWxOpen().getAppSecret())
                .build();
        this.authRequest = new AuthWechatMiniProgramRequest(config);
    }

    public AuthUser login(String code) {
        if (code == null || code.isBlank()) {
            throw AuthorityBusinessException.invalidParameter("微信开放平台登录code不能为空");
        }
        AuthCallback callback = AuthCallback.builder().code(code).build();
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            throw new AuthException(response.getMsg());
        }
        return response.getData();
    }
}
