package com.lambda.fusion.permission.server;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.RemotePermissionReportService;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.PermissionRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RemotePermissionReportServiceImpl implements RemotePermissionReportService {
    private final PermissionRegistry permissionRegistry;
    private final PermissionProperties permissionProperties;

    @Override
    public void report(PermissionPushRequest request, String token) {
        verifyToken(token);
        permissionRegistry.updateReport(request);
    }

    private void verifyToken(String token) {
        String expected = permissionProperties.getServer().getAuthToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(token)) {
            throw new SecurityException("invalid permission token");
        }
    }
}
