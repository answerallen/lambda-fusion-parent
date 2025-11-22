package com.lambda.fusion.core.convert;

import java.time.LocalDateTime;
import org.mapstruct.Named;

public interface ConvertFunctions {

    @Named("mapAccountExpired")
    static Boolean mapAccountExpired(LocalDateTime expiredTime) {
        if (expiredTime == null) {
            return false;
        }
        return expiredTime.isAfter(LocalDateTime.now());
    }
}
