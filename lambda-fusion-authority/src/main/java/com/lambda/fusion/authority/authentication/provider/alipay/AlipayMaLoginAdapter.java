package com.lambda.fusion.authority.authentication.provider.alipay;

import com.alipay.api.AlipayConfig;
import com.lambda.fusion.authority.AuthorityProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthAlipayCertRequest;

public class AlipayMaLoginAdapter {

    private final AuthAlipayCertRequest authRequest;

    public AlipayMaLoginAdapter(AuthorityProperties.ThirdPartConfig thirdPartConfig) {
        AuthConfig authConfig = AuthConfig.builder()
                .clientId(thirdPartConfig.getAlipayMa().getAppId())
                .clientSecret(thirdPartConfig.getAlipayMa().getPrivateKey())
                .ignoreCheckRedirectUri(true)
                .build();

        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setAppId(thirdPartConfig.getAlipayMa().getAppId());
        alipayConfig.setPrivateKey(thirdPartConfig.getAlipayMa().getPrivateKey());
        alipayConfig.setAppCertPath(thirdPartConfig.getAlipayMa().getAppCertPath());
        alipayConfig.setAlipayPublicCertPath(thirdPartConfig.getAlipayMa().getAlipayPublicCertPath());
        alipayConfig.setRootCertPath(thirdPartConfig.getAlipayMa().getRootCertPath());

        this.authRequest = new AuthAlipayCertRequest(authConfig, alipayConfig);
    }

    public AuthUser login(String authCode) {
        AuthCallback callback = AuthCallback.builder().auth_code(authCode).build();
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            throw new AuthException(response.getMsg());
        }
        return response.getData();
    }
}
