package com.lambda.fusion.permission.server;

import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.loader.LocalPermissionLoader;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import com.lambda.fusion.permission.service.PermissionTokenVerifier;
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
    private final PermissionTokenVerifier permissionTokenVerifier;
    private final LocalPermissionLoader localPermissionLoader;
    private final String applicationName;

    @Override
    public void syncPermissions(PermissionPushRequest request, String token) {
        permissionTokenVerifier.verify(request == null ? null : request.getApplication(), token);
        apiPermissionRegistry.updateReport(request);
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        List<PermissionFileMetadata> files = localPermissionLoader.load();
        apiPermissionRegistry.replaceLocal(files, applicationName);
    }
}
