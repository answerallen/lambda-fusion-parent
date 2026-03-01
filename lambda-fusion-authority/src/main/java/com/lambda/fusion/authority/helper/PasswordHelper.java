package com.lambda.fusion.authority.helper;

import cn.hutool.core.lang.UUID;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.domain.user.Password;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

public class PasswordHelper {

    public static Password obtainPassword(AuthorityProperties.PasswordStrategy strategy, String parameter) {
        String origin;
        String password;
        AuthorityProperties.PasswordStrategy.Mode mode = strategy.getMode();
        String customize = strategy.getCustomize();
        switch (mode) {
            case RANDOM:
                origin = UUID.randomUUID().toString();
                password = md5f2(origin);
                break;
            case CIPHERTEXT:
                if (StringUtils.isNotBlank(parameter)) {
                    origin = null;
                    password = parameter;
                } else {
                    origin = customize;
                    password = md5f2(customize);
                }
                break;
            default:
                origin = customize;
                password = md5f2(customize);
        }
        return new Password(origin, password);
    }

    public static String md5f2(String password) {
        return DigestUtils.md5Hex(DigestUtils.md5Hex(password));
    }
}
