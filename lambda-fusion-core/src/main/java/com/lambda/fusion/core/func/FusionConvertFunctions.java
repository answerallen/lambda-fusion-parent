package com.lambda.fusion.core.func;

import java.time.LocalDateTime;
import org.mapstruct.Named;

public interface FusionConvertFunctions {

    @Named("mapAccountExpired")
    static Boolean mapAccountExpired(LocalDateTime expiredTime) {
        if (expiredTime == null) {
            return false;
        }
        return expiredTime.isAfter(LocalDateTime.now());
    }
}
