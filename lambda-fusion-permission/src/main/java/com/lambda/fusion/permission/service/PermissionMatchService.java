package com.lambda.fusion.permission.service;

import com.lambda.fusion.permission.model.ApiPermissionMetadata;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class PermissionMatchService {
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public Optional<ApiPermissionMetadata> match(List<ApiPermissionMetadata> apis, String method, String path) {
        if (apis == null || apis.isEmpty() || method == null || path == null) {
            return Optional.empty();
        }
        String methodUpper = method.toUpperCase(Locale.ROOT);
        String normalizedPath = normalize(path);
        for (ApiPermissionMetadata api : apis) {
            if (api == null || api.getPath() == null || api.getMethod() == null) {
                continue;
            }
            if (!methodUpper.equals(api.getMethod().toUpperCase(Locale.ROOT))) {
                continue;
            }
            String pattern = toAntPattern(api.getPath());
            if (antPathMatcher.match(pattern, normalizedPath)) {
                return Optional.of(api);
            }
        }
        return Optional.empty();
    }

    private String toAntPattern(String path) {
        String normalized = normalize(path);
        return normalized.replaceAll("\\{[^/]+}", "*");
    }

    private String normalize(String path) {
        String value = path.trim().replaceAll("/+", "/");
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
