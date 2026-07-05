package com.lambda.fusion.authority.authentication.provider.wechat;

import com.lambda.fusion.authority.AuthorityProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;

public class WechatMaLoginAdapter {

    private final AuthWechatMiniProgramRequest authRequest;

    public WechatMaLoginAdapter(AuthorityProperties.ThirdPartConfig partConfig) {
        AuthConfig config = AuthConfig.builder()
                .clientId(partConfig.getWxMa().getAppId())
                .clientSecret(partConfig.getWxMa().getAppSecret())
                .redirectUri(partConfig.getWxMa().getRedirectUri())
                .build();
        this.authRequest = new AuthWechatMiniProgramRequest(config);
    }

    public AuthUser login(String code) {
        AuthCallback callback = AuthCallback.builder().code(code).build();
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            throw new AuthException(response.getMsg());
        }
        return response.getData();
    }
}
