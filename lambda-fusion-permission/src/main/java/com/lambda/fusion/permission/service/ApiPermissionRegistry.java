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
import org.springframework.stereotype.Component;

@Component
public class ApiPermissionRegistry {
    private final AtomicReference<List<ApiPermissionMetadata>> localApis = new AtomicReference<>(List.of());

    private final Map<String, List<ApiPermissionMetadata>> reportedApis = new ConcurrentHashMap<>();

    public void replaceLocal(List<PermissionFileMetadata> files) {
        List<PermissionFileMetadata> safeFiles = files == null ? List.of() : List.copyOf(files);
        localApis.set(mergeFiles(safeFiles));
    }

    private void checkAndMerged(List<ApiPermissionMetadata> merged, PermissionFileMetadata file) {
        if (file == null || file.getApis() == null) {
            return;
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

    public List<ApiPermissionMetadata> getLocalApis() {
        return localApis.get();
    }

    public void updateReport(PermissionPushRequest request) {
        if (request == null
                || request.getApplication() == null
                || request.getApplication().isBlank()) {
            return;
        }
        reportedApis.put(request.getApplication(), mergeFiles(request.getFiles()));
    }

    public List<ApiPermissionMetadata> getReportedApis(String application) {
        if (application == null || application.isBlank()) {
            return List.of();
        }
        return reportedApis.getOrDefault(application, List.of());
    }

    private List<ApiPermissionMetadata> mergeFiles(List<PermissionFileMetadata> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<ApiPermissionMetadata> merged = new ArrayList<>();
        for (PermissionFileMetadata file : files) {
            checkAndMerged(merged, file);
        }
        return Collections.unmodifiableList(merged);
    }
}
