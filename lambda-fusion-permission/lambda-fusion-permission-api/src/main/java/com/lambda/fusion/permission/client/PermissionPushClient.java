package com.lambda.fusion.permission.client;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class PermissionPushClient {
    private final PermissionProperties properties;
    private final PermissionSyncApi permissionSyncApi;

    public PermissionPushClient(PermissionProperties properties, PermissionSyncApi permissionSyncApi) {
        this.properties = properties;
        this.permissionSyncApi = permissionSyncApi;
    }

    public void push(PermissionPushRequest request) {
        permissionSyncApi.syncPermissions(request, properties.getClient().getAuthToken());
    }
}
