package com.lambda.fusion.permission.client;

import com.lambda.fusion.permission.PermissionConstants;
import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.api.RemotePermissionReportService;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;

@RequiredArgsConstructor
public class PermissionPushClient {
    private final PermissionProperties properties;

    @DubboReference(version = PermissionConstants.DUBBO_VERSION, group = PermissionConstants.DUBBO_GROUP, check = false)
    private RemotePermissionReportService remotePermissionReportService;

    public void push(PermissionPushRequest request) {
        remotePermissionReportService.report(request, properties.getClient().getAuthToken());
    }
}
