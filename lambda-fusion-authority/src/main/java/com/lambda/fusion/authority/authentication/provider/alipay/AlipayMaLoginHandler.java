package com.lambda.fusion.authority.authentication.provider.alipay;

import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.service.ThirdPartyLoginService;

public class AlipayMaLoginHandler extends AbstractThirdPartLoginProvider<AlipayMaLoginHandler>
        implements ThirdPartLoginHandler {

    private final AlipayMaLoginAdapter adapter;

    public AlipayMaLoginHandler(
            ThirdPartyLoginService thirdPartyLoginService,
            AlipayMaLoginHandler thirdPartLoginHandler,
            AlipayMaLoginAdapter adapter) {
        super(thirdPartyLoginService, thirdPartLoginHandler);
        this.adapter = adapter;
    }

    @Override
    public Object handle(String loginParam) {
        return adapter.login(loginParam);
    }

    @Override
    public String getThirdType() {
        return AuthorityConstants.ThirdType.ALIPAY_MA.getCode();
    }
}
