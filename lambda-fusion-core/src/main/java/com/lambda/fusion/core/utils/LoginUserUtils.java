package com.lambda.fusion.core.utils;

import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.identity.LoginUserDetails;

public class LoginUserUtils {

    public static LoginUserDetails getLoginUser() {
        return OperatorUtils.getLoginUser(LoginUserDetails.class);
    }

    public static String getTenantId() {
        String tenantId = getLoginUser().getTenantId();
        return StrUtil.nullToDefault(tenantId, "");
    }
}
