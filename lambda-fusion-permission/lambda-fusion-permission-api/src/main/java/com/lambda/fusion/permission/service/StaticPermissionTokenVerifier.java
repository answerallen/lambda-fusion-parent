package com.lambda.fusion.permission.service;

import com.lambda.fusion.permission.PermissionProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 基于静态配置令牌的默认校验器
 *
 * <p>所有上报方共用 {@code lambda.fusion.permission.server.auth-token} 配置的单一令牌。
 * 适用于不提供服务注册表（服务密钥）的部署形态。
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class StaticPermissionTokenVerifier implements PermissionTokenVerifier {

    private final PermissionProperties permissionProperties;

    public StaticPermissionTokenVerifier(PermissionProperties permissionProperties) {
        this.permissionProperties = permissionProperties;
    }

    @Override
    public void verify(String application, String token) {
        String expected = permissionProperties.getServer().getAuthToken();
        if (expected == null || expected.isBlank()) {
            throw new SecurityException("permission sync token is not configured on server");
        }
        if (token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("invalid permission token");
        }
    }
}
