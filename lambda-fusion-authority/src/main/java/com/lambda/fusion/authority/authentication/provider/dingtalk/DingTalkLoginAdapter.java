package com.lambda.fusion.authority.authentication.provider.dingtalk;

import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthDingTalkV2Request;

public class DingTalkLoginAdapter {

    private final AuthDingTalkV2Request authRequest;

    public DingTalkLoginAdapter(AuthorityProperties.ThirdPartConfig partConfig) {
        AuthConfig config = AuthConfig.builder()
                .clientId(partConfig.getDingTalk().getAppId())
                .clientSecret(partConfig.getDingTalk().getAppSecret())
                .build();
        this.authRequest = new AuthDingTalkV2Request(config);
    }

    public AuthUser login(String code) {
        if (code == null || code.isBlank()) {
            throw AuthorityBusinessException.invalidParameter("钉钉登录code不能为空");
        }
        AuthCallback callback = AuthCallback.builder().code(code).build();
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            throw new AuthException(response.getMsg());
        }
        return response.getData();
    }
}
