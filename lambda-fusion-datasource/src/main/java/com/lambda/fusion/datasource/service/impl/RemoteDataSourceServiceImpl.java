package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.datasource.api.DataSourceChangeCallback;
import com.lambda.fusion.datasource.manager.DataSourceCallbackManager;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.service.RemoteDataSourceService;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

@Slf4j
@DubboService(version = "1.0.0", group = "datasource")
@RequiredArgsConstructor
public class RemoteDataSourceServiceImpl implements RemoteDataSourceService {

    private final DataSourceManageService globalService;
    private final TenantDataSourceManageService tenantService;
    private final DataSourceCallbackManager callbackManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RemoteDataSource> listActiveDataSources() {

        // 1. 加载全局已启用数据源
        List<RemoteDataSource> globals = globalService.listAll().stream()
                .filter(e -> e.getEnabled() != null && e.getEnabled() == 1)
                .map(this::toRemoteDataSource)
                .toList();
        List<RemoteDataSource> result = new ArrayList<>(globals);

        // 2. 加载当前租户专属已启用数据源
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(currentTenantId) && !"default".equals(currentTenantId)) {
            List<RemoteDataSource> tenants = tenantService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId)
                            .eq(TenantDataSourceEntity::getEnabled, 1))
                    .stream()
                    .map(this::toRemoteDataSource)
                    .toList();
            result.addAll(tenants);
        }

        return result;
    }

    @Override
    public void subscribe(String clientId, DataSourceChangeCallback callback) {
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        callbackManager.addSubscriber(clientId, currentTenantId, callback);
    }

    @Override
    public void unsubscribe(String clientId) {
        callbackManager.removeSubscriber(clientId);
    }

    @Override
    public Map<String, Long> getDataSourcesVersion() {
        return listActiveDataSources().stream()
                .collect(Collectors.toMap(RemoteDataSource::getId, RemoteDataSource::getVersion));
    }

    public RemoteDataSource toRemoteDataSource(DataSourceEntity entity) {
        RemoteDataSource dto = new RemoteDataSource();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDatasourceName());
        dto.setDriverClassName(entity.getDriverClassName());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setPassword(entity.getPassword());
        dto.setEnabled(entity.getEnabled());
        dto.setTenantId(null);
        // 使用哈希码作为版本号以保持一致性
        dto.setVersion(Objects.hash(
                entity.getId(),
                entity.getDatasourceName(),
                entity.getDriverClassName(),
                entity.getJdbcUrl(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getEnabled()));
        return dto;
    }

    private RemoteDataSource toRemoteDataSource(TenantDataSourceEntity entity) {
        RemoteDataSource dto = new RemoteDataSource();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDbName());
        dto.setEnabled(entity.getEnabled());
        dto.setTenantId(entity.getTenantId());

        if (entity.getUpdateTime() != null) {
            dto.setVersion(entity.getUpdateTime().getTime());
        } else {
            dto.setVersion(entity.getCreateTime().getTime());
        }
        return getRemoteDataSource(entity, dto, objectMapper);
    }

    public static RemoteDataSource getRemoteDataSource(
            TenantDataSourceEntity entity, RemoteDataSource remoteDataSource, ObjectMapper objectMapper) {
        try {
            if (StringUtils.hasText(entity.getConfiguration())) {
                // 解析配置
                JsonNode node = objectMapper.readTree(entity.getConfiguration());
                if (node.has("jdbcUrl"))
                    remoteDataSource.setJdbcUrl(node.get("jdbcUrl").asText());
                else if (node.has("url"))
                    remoteDataSource.setJdbcUrl(node.get("url").asText());

                if (node.has("username"))
                    remoteDataSource.setUsername(node.get("username").asText());
                if (node.has("password"))
                    remoteDataSource.setPassword(node.get("password").asText());
                if (node.has("driverClassName"))
                    remoteDataSource.setDriverClassName(
                            node.get("driverClassName").asText());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse configuration for tenant datasource " + entity.getId(), e);
        }

        return remoteDataSource;
    }
}
