package com.lambda.fusion.authority.authentication.provider.alipay;

import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.authentication.model.ThirdPartyUser;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.service.ThirdPartyLoginService;
import me.zhyd.oauth.model.AuthUser;

public class AlipayMaLoginHandler extends AbstractThirdPartLoginProvider<AlipayMaLoginHandler>
        implements ThirdPartLoginHandler {

    private final AlipayMaLoginAdapter adapter;

    public AlipayMaLoginHandler(ThirdPartyLoginService thirdPartyLoginService, AlipayMaLoginAdapter adapter) {
        super(thirdPartyLoginService);
        super.setThirdPartLoginHandler(this);
        this.adapter = adapter;
    }

    @Override
    public Object handle(String loginParam) {
        AuthUser authUser = adapter.login(loginParam);
        ThirdPartyUser thirdPartyUser = new ThirdPartyUser();
        thirdPartyUser.setThirdType(AuthorityConstants.ThirdType.ALIPAY_MA.getCode());
        thirdPartyUser.setAvatar(authUser.getAvatar());
        thirdPartyUser.setNickname(authUser.getNickname());
        thirdPartyUser.setOpenId(authUser.getUsername());
        thirdPartyUser.setRemark(authUser.getRemark());
        return thirdPartyUser;
    }

    @Override
    public String getThirdType() {
        return AuthorityConstants.ThirdType.ALIPAY_MA.getCode();
    }
}
