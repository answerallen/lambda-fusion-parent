package com.fusion.boot.interceptor;

import cn.dev33.satoken.stp.StpLogic;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.security.inteceptor.SecureInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FusionSecurityInterceptor implements SecureInterceptor {

    public FusionSecurityInterceptor() {
        log.trace("FusionSecureInterceptor init....");
    }

    @Override
    public void handle(Object handler, StpLogic stpLogic, LoginUser operator) {
        stpLogic.checkLogin();
    }
}
