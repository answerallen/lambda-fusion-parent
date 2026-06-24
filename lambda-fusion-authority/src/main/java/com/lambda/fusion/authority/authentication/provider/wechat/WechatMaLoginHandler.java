package com.lambda.fusion.authority.authentication.provider.wechat;

import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.authentication.model.ThirdPartyUser;
import com.lambda.security.provider.AbstractThirdPartLoginProvider;
import com.lambda.security.provider.ThirdPartLoginHandler;
import com.lambda.security.service.ThirdPartyLoginService;
import me.zhyd.oauth.model.AuthUser;

public class WechatMaLoginHandler extends AbstractThirdPartLoginProvider<WechatMaLoginHandler>
        implements ThirdPartLoginHandler {

    private final WechatMaLoginAdapter adapter;

    public WechatMaLoginHandler(ThirdPartyLoginService thirdPartyLoginService, WechatMaLoginAdapter adapter) {
        super(thirdPartyLoginService);
        super.setThirdPartLoginHandler(this);
        this.adapter = adapter;
    }

    @Override
    public Object handle(String loginParam) {
        AuthUser authUser = adapter.login(loginParam);
        ThirdPartyUser thirdPartyUser = new ThirdPartyUser();
        thirdPartyUser.setThirdType(AuthorityConstants.ThirdType.WX_MA.getCode());
        thirdPartyUser.setAvatar(authUser.getAvatar());
        thirdPartyUser.setNickname(authUser.getNickname());
        thirdPartyUser.setOpenId(authUser.getUsername());
        thirdPartyUser.setRemark(authUser.getRemark());
        return thirdPartyUser;
    }

    @Override
    public String getThirdType() {
        return AuthorityConstants.ThirdType.WX_MA.getCode();
    }
}
