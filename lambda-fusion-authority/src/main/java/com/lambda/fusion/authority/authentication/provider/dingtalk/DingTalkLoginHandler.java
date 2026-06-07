package com.lambda.fusion.authority.authentication.provider.dingtalk;

import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.service.ThirdPartyLoginService;

public class DingTalkLoginHandler extends AbstractThirdPartLoginProvider<DingTalkLoginHandler> implements ThirdPartLoginHandler {

    private final DingTalkLoginAdapter adapter;

    public DingTalkLoginHandler(ThirdPartyLoginService thirdPartyLoginService, DingTalkLoginAdapter adapter) {
        super(thirdPartyLoginService);
        super.setThirdPartLoginHandler(this);
        this.adapter = adapter;
    }


    @Override
    public Object handle(String loginParam) {
        return adapter.login(loginParam);
    }

    @Override
    public boolean support(String thirdType) {
        return false;
    }

    @Override
    public String getThirdType() {
        return AuthorityConstants.ThirdType.DING_TALK.getCode();
    }
}
