package com.lambda.fusion.permission.server;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.loader.LocalPermissionLoader;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class PermissionSyncApiImpl implements PermissionSyncApi, ApplicationRunner {
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final PermissionProperties permissionProperties;
    private final LocalPermissionLoader localPermissionLoader;
    private final String applicationName;

    @Override
    public void syncPermissions(PermissionPushRequest request, String token) {
        verifyToken(token);
        apiPermissionRegistry.updateReport(request);
    }

    private void verifyToken(String token) {
        String expected = permissionProperties.getServer().getAuthToken();
        if (expected == null || expected.isBlank()) {
            throw new SecurityException("permission sync token is not configured on server");
        }
        if (token == null
                || !java.security.MessageDigest.isEqual(
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new SecurityException("invalid permission token");
        }
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        List<PermissionFileMetadata> files = localPermissionLoader.load();
        apiPermissionRegistry.replaceLocal(files, applicationName);
    }
}
