package com.lambda.fusion.core.utils;

import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.core.identity.UserDetails;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthUtils {

    public static UserDetails getUser() {
        return OperatorUtils.getLoginUser(UserDetails.class);
    }

    public static String getTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (StrUtil.isBlank(tenantId)) {
            tenantId = getUser().getTenantId();
        }
        return StrUtil.nullToDefault(tenantId, "");
    }
}
