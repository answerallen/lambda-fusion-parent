package com.lambda.fusion.core.utils;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.identity.UserPrincipal;

public class LoginUserUtils {

    public static UserPrincipal getLoginUser() {
        return OperatorUtils.getLoginUser(UserPrincipal.class);
    }
}
