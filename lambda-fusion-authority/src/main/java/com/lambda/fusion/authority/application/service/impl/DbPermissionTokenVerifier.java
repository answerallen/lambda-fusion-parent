package com.lambda.fusion.authority.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.fusion.authority.application.model.ApplicationEntity;
import com.lambda.fusion.authority.application.service.ApplicationService;
import com.lambda.fusion.permission.service.PermissionTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 基于服务注册表的权限同步令牌校验器
 *
 * <p>按上报方的应用名（spring.application.name）查询 la_applications 表中的服务密钥进行校验，
 * 每个服务拥有独立密钥，替代 PermissionProperties 的单一静态令牌。
 */
@RequiredArgsConstructor
public class DbPermissionTokenVerifier implements PermissionTokenVerifier {

    private final ApplicationService applicationService;

    @Override
    public void verify(String application, String token) {
        if (StringUtils.isBlank(application)) {
            throw new SecurityException("permission sync application is required");
        }
        ApplicationEntity entity = applicationService.getOne(Wrappers.<ApplicationEntity>lambdaQuery()
                .eq(ApplicationEntity::getSpringApplicationName, application)
                .last("limit 1"));
        if (entity == null) {
            throw new SecurityException("application not registered: " + application);
        }
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            throw new SecurityException("application disabled: " + application);
        }
        String expected = entity.getSecret();
        if (StringUtils.isBlank(expected)) {
            throw new SecurityException("application secret is not configured: " + application);
        }
        if (token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("invalid permission token for application: " + application);
        }
    }
}
