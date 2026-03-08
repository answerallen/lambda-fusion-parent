package com.lambda.fusion.permission.api;

import com.lambda.fusion.permission.model.PermissionPushRequest;

public interface RemotePermissionReportService {
    void report(PermissionPushRequest request, String token);
}
