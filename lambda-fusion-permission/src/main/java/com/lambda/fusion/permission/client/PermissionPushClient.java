package com.lambda.fusion.permission.client;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
public class PermissionPushClient {
    private final PermissionProperties properties;
    private final RestTemplate restTemplate;

    public void push(PermissionPushRequest request) {
        String baseUrl = properties.getClient().getServerBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("permission client serverBaseUrl is required when pushEnabled=true");
        }
        String url = buildUrl(baseUrl, properties.getClient().getReportPath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getClient().getAuthToken() != null && !properties.getClient().getAuthToken().isBlank()) {
            headers.set("X-Permission-Token", properties.getClient().getAuthToken());
        }
        restTemplate.postForEntity(url, new HttpEntity<>(request, headers), Void.class);
    }

    private String buildUrl(String baseUrl, String reportPath) {
        if (reportPath == null || reportPath.isBlank()) {
            throw new IllegalStateException("permission client reportPath is required when pushEnabled=true");
        }
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = reportPath.startsWith("/") ? reportPath : "/" + reportPath;
        return left + right;
    }
}
