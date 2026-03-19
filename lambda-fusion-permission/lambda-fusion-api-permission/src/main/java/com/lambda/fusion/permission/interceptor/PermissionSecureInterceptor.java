package com.lambda.fusion.permission.interceptor;

import cn.dev33.satoken.stp.StpLogic;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.model.ApiPermissionMetadata;
import com.lambda.fusion.permission.service.ApiPermissionMatcher;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import com.lambda.security.inteceptor.SecureInterceptor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class PermissionSecureInterceptor implements SecureInterceptor {
    private final PermissionProperties properties;
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final ApiPermissionMatcher apiPermissionMatcher;

    @Override
    public void handle(Object handler, StpLogic stpLogic, LoginUser operator) {
        stpLogic.checkLogin();
        if (!properties.getClient().isCheckEnabled()) {
            return;
        }
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }
        HttpServletRequest request = requestAttributes.getRequest();
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        Optional<ApiPermissionMetadata> matched =
                apiPermissionMatcher.match(apiPermissionRegistry.getLocalApis(), request.getMethod(), path);
        if (matched.isEmpty()) {
            if (properties.getClient().isDenyUnmatched()) {
                throw new SecurityException("permission metadata not matched: " + request.getMethod() + " " + path);
            }
            return;
        }
        List<String> permissions = matched.get().getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            stpLogic.checkPermission(permission);
        }
    }
}
