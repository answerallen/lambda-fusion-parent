package com.lambda.fusion.permission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPermissionLoader {
    private final PermissionProperties properties;
    private final ObjectMapper objectMapper;

    public List<PermissionFileMetadata> load() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String path = properties.getClient().getResourcePath();
        String pattern = path.startsWith("classpath*:") ? path : "classpath*:" + path;
        try {
            Resource[] resources = resolver.getResources(pattern);
            List<PermissionFileMetadata> files = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                PermissionFileMetadata metadata = objectMapper.readValue(resource.getInputStream(), PermissionFileMetadata.class);
                files.add(metadata);
            }
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("load permission json failed: " + pattern, e);
        }
    }
}
