package com.lambda.fusion.authority.authentication.provider.wechat;

import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.service.ThirdPartyLoginService;

public class WechatMaLoginHandler extends AbstractThirdPartLoginProvider<WechatMaLoginHandler>
        implements ThirdPartLoginHandler {

    private final WechatMaLoginAdapter adapter;

    public WechatMaLoginHandler(
            ThirdPartyLoginService thirdPartyLoginService,
            WechatMaLoginAdapter adapter) {
        super(thirdPartyLoginService);
        super.setThirdPartLoginHandler(this);
        this.adapter = adapter;
    }

    @Override
    public Object handle(String loginParam) {
        return adapter.login(loginParam);
    }

    @Override
    public String getThirdType() {
        return AuthorityConstants.ThirdType.WX_MA.getCode();
    }
}
