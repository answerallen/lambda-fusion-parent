package com.lambda.fusion.core.convert;

import cn.hutool.core.codec.Base64;
import java.time.LocalDateTime;
import org.mapstruct.Named;

@SuppressWarnings("unused")
public interface FusionConvertFunctions {

    @Named("mapAccountExpired")
    static Boolean mapAccountExpired(LocalDateTime expiredTime) {
        if (expiredTime == null) {
            return false;
        }
        return !expiredTime.isAfter(LocalDateTime.now());
    }

    @Named("mapAccountLocked")
    static Boolean mapAccountLocked(boolean enabled) {
        return !enabled;
    }

    @Named("mapAccountEnabled")
    static Integer mapAccountEnabled(boolean enabled) {
        return enabled ? 1 : 0;
    }

    @Named("mapEncodePassword")
    static String mapAccountEnabled(String password) {
        return Base64.encode(password);
    }
}
