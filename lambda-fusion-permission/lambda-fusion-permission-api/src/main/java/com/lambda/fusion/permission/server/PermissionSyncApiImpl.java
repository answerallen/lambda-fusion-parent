package com.lambda.fusion.permission.server;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class PermissionSyncApiImpl implements PermissionSyncApi {
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final PermissionProperties permissionProperties;

    @Override
    public void syncPermissions(PermissionPushRequest request, String token) {
        verifyToken(token);
        apiPermissionRegistry.updateReport(request);
    }

    private void verifyToken(String token) {
        String expected = permissionProperties.getServer().getAuthToken();
        // If not configured, deny by default for security
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
}
