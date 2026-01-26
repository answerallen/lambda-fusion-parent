package com.lambda.fusion.core.utils;

import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.identity.UserPrincipal;

public class LoginUserUtils {

    public static UserPrincipal getLoginUser() {
        return OperatorUtils.getLoginUser(UserPrincipal.class);
    }

    public static String getTenantId() {
        String tenantId = getLoginUser().getTenantId();
        return StrUtil.nullToDefault(tenantId,"");
    }
}
