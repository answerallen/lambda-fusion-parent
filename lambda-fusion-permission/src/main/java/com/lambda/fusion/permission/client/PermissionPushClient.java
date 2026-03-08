package com.lambda.fusion.permission.client;

import com.lambda.fusion.permission.PermissionConstants;
import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;

@RequiredArgsConstructor
public class PermissionPushClient {
    private final PermissionProperties properties;

    @DubboReference(version = PermissionConstants.DUBBO_VERSION, group = PermissionConstants.DUBBO_GROUP, check = false)
    private PermissionSyncApi permissionSyncApi;

    public void push(PermissionPushRequest request) {
        permissionSyncApi.syncPermissions(request, properties.getClient().getAuthToken());
    }
}
