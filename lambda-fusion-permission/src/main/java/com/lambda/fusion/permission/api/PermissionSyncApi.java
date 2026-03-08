package com.lambda.fusion.permission.api;

import com.lambda.fusion.permission.model.PermissionPushRequest;

public interface PermissionSyncApi {
    void syncPermissions(PermissionPushRequest request, String token);
}
