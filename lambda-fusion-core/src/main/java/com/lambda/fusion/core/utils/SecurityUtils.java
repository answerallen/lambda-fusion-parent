package com.lambda.fusion.core.utils;

import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.identity.LoginUserDetails;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityUtils {

    public static LoginUserDetails getUser() {
        return OperatorUtils.getLoginUser(LoginUserDetails.class);
    }

    public static String getTenantId() {
        String tenantId = getUser().getTenantId();
        return StrUtil.nullToDefault(tenantId, "");
    }
}
