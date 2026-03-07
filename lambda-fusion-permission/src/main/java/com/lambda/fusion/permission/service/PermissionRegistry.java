package com.lambda.fusion.permission.service;

import com.lambda.fusion.permission.model.ApiPermissionMetadata;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class PermissionRegistry {
    private final AtomicReference<List<PermissionFileMetadata>> localFiles = new AtomicReference<>(List.of());
    private final AtomicReference<List<ApiPermissionMetadata>> localApis = new AtomicReference<>(List.of());
    @Getter
    private final Map<String, PermissionPushRequest> reports = new ConcurrentHashMap<>();

    public void replaceLocal(List<PermissionFileMetadata> files) {
        List<PermissionFileMetadata> safeFiles = files == null ? List.of() : List.copyOf(files);
        localFiles.set(safeFiles);
        List<ApiPermissionMetadata> merged = new ArrayList<>();
        for (PermissionFileMetadata file : safeFiles) {
            if (file == null || file.getApis() == null) {
                continue;
            }
            for (ApiPermissionMetadata api : file.getApis()) {
                if (api == null) {
                    continue;
                }
                if (api.getModule() == null || api.getModule().isBlank()) {
                    api.setModule(file.getModule());
                }
                merged.add(api);
            }
        }
        localApis.set(Collections.unmodifiableList(merged));
    }

    public List<PermissionFileMetadata> getLocalFiles() {
        return localFiles.get();
    }

    public List<ApiPermissionMetadata> getLocalApis() {
        return localApis.get();
    }

    public void updateReport(PermissionPushRequest request) {
        if (request == null || request.getApplication() == null || request.getApplication().isBlank()) {
            return;
        }
        reports.put(request.getApplication(), request);
    }

    public List<ApiPermissionMetadata> getReportedApis(String application) {
        PermissionPushRequest request = reports.get(application);
        if (request == null || request.getFiles() == null) {
            return List.of();
        }
        List<ApiPermissionMetadata> result = new ArrayList<>();
        for (PermissionFileMetadata file : request.getFiles()) {
            if (file == null || file.getApis() == null) {
                continue;
            }
            for (ApiPermissionMetadata api : file.getApis()) {
                if (api == null) {
                    continue;
                }
                if (api.getModule() == null || api.getModule().isBlank()) {
                    api.setModule(file.getModule());
                }
                result.add(api);
            }
        }
        return result;
    }
}
