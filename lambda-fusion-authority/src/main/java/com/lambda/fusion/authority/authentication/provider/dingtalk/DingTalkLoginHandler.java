package com.lambda.fusion.authority.authentication.provider.dingtalk;

import cn.chargemind.auth.AuthConstants;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.provider.ThirdPartLoginResult;
import com.lambda.security.service.ThirdPartyLoginService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DingTalkLoginAdapter.class)
public class DingTalkLoginHandler extends AbstractThirdPartLoginProvider<DingTalkLoginHandler> implements ThirdPartLoginHandler {

    private final DingTalkLoginAdapter adapter;

    public DingTalkLoginHandler(ThirdPartyLoginService thirdPartyLoginService, DingTalkLoginHandler thirdPartLoginHandler, DingTalkLoginAdapter adapter) {
        super(thirdPartyLoginService, thirdPartLoginHandler);
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
        return AuthConstants.ThirdType.DINGTALK.getCode();
    }

    @Override
    public ThirdPartLoginResult getThirdLoginParam(String code) {
        return null;
    }
}
