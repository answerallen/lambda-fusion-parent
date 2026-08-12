package com.lambda.fusion.core.utils;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.core.identity.UserDetails;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthUtils {

    public static UserDetails getUser() {
        return OperatorUtils.getLoginUser(UserDetails.class);
    }

    public static String getTenantId() {
        return getUser().getTenantId();
    }
}
