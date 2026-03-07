package com.lambda.fusion.permission.server;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.model.ApiPermissionMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.PermissionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/permission/apis")
public class PermissionReportController {
    private final PermissionRegistry permissionRegistry;
    private final PermissionProperties permissionProperties;

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(
            @RequestBody PermissionPushRequest request,
            @RequestHeader(value = "X-Permission-Token", required = false) String token) {
        verifyToken(token);
        permissionRegistry.updateReport(request);
    }

    @GetMapping("/applications")
    public List<String> applications() {
        return new ArrayList<>(permissionRegistry.getReports().keySet());
    }

    @GetMapping("/applications/{application}")
    public PermissionPushRequest application(@PathVariable String application) {
        PermissionPushRequest request = permissionRegistry.getReports().get(application);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found");
        }
        return request;
    }

    @GetMapping("/applications/{application}/apis")
    public List<ApiPermissionMetadata> apis(@PathVariable String application) {
        return permissionRegistry.getReportedApis(application);
    }

    @GetMapping("/reported")
    public Map<String, PermissionPushRequest> reported() {
        return permissionRegistry.getReports();
    }

    private void verifyToken(String token) {
        String expected = permissionProperties.getServer().getAuthToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid permission token");
        }
    }
}
