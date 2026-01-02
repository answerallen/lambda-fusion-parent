package com.lambda.fusion.datasource.api;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.model.*;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 远程数据源服务Server端实现
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteDataSourceServiceImpl implements RemoteDataSourceService {

    private final DataSourceManageService dataSourceManageService;
    private final TenantDataSourceManageService tenantDataSourceManageService;
    private final DataSourceChangeDispatcher callbackManager;
    private final ObjectMapper objectMapper;

    @Override
    public List<RemoteDataSource> listAll() {
        // 1. 全局数据源
        List<RemoteDataSource> globals =
                dataSourceManageService.listAll().stream().map(this::toDTO).collect(Collectors.toList());

        // 2. 当前租户数据源 (如果有上下文)
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(currentTenantId) && !"default".equals(currentTenantId)) {
            List<RemoteDataSource> tenants = tenantDataSourceManageService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId))
                    .stream()
                    .map(this::toDTO)
                    .toList();
            globals.addAll(tenants);
        }
        return globals;
    }

    @Override
    public List<RemoteDataSource> listEnabled() {
        // 1. 全局已启用数据源
        List<RemoteDataSource> globals = dataSourceManageService.listAll().stream()
                .filter(e -> e.getEnabled() != null && e.getEnabled() == 1)
                .map(this::toDTO)
                .collect(Collectors.toList());

        // 2. 当前租户已启用数据源
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(currentTenantId) && !"default".equals(currentTenantId)) {
            List<RemoteDataSource> tenants = tenantDataSourceManageService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId)
                            .eq(TenantDataSourceEntity::getEnabled, 1))
                    .stream()
                    .map(this::toDTO)
                    .toList();
            globals.addAll(tenants);
        }
        return globals;
    }

    @Override
    public RemoteDataSource get(String id) {
        // 先查全局
        DataSourceEntity global = dataSourceManageService.get(id);
        if (global != null) {
            return toDTO(global);
        }
        // 再查租户
        TenantDataSourceEntity tenant = tenantDataSourceManageService.get(id);
        if (tenant != null) {
            // 校验租户权限
            String currentTenantId = DubboContextHolder.getCurrentTenantId();
            if (!StringUtils.hasText(currentTenantId)
                    || "default".equals(currentTenantId)
                    || currentTenantId.equals(tenant.getTenantId())) {
                return toDTO(tenant);
            }
        }
        return null;
    }

    @Override
    public boolean add(RemoteDataSource dto) {
        try {
            if (StringUtils.hasText(dto.getTenantId())) {
                checkPermission(dto.getTenantId());
                UpsertTenantDataSource input = toUpsertTenantDataSource(dto);
                tenantDataSourceManageService.save(input);
            } else {
                checkPermission(null);
                UpsertDataSource input = toUpsertDataSource(dto);
                dataSourceManageService.save(input);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to add datasource: {}", dto.getId(), e);
            return false;
        }
    }

    @Override
    public boolean update(String id, RemoteDataSource dto) {
        try {
            if (StringUtils.hasText(dto.getTenantId())) {
                checkPermission(dto.getTenantId());
                UpsertTenantDataSource input = toUpsertTenantDataSource(dto);
                input.setId(id);
                tenantDataSourceManageService.update(id, input);
                dto.setId(id);
            } else {
                checkPermission(null);
                UpsertDataSource input = toUpsertDataSource(dto);
                input.setId(id);
                dataSourceManageService.update(id, input);
                dto.setId(id);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to update datasource: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            DataSourceEntity global = dataSourceManageService.get(id);
            if (global != null) {
                checkPermission(null);
                dataSourceManageService.delete(id);
                return true;
            }

            TenantDataSourceEntity tenant = tenantDataSourceManageService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                tenantDataSourceManageService.delete(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete datasource: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean test(String id) {
        return dataSourceManageService.test(id);
    }

    @Override
    public boolean enable(String id) {
        try {
            DataSourceEntity global = dataSourceManageService.get(id);
            if (global != null) {
                checkPermission(null);
                dataSourceManageService.enable(id);
                return true;
            }
            TenantDataSourceEntity tenant = tenantDataSourceManageService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                UpsertTenantDataSource input = new UpsertTenantDataSource();
                input.setId(id);
                input.setEnabled(1);
                input.setDbName(tenant.getDbName());
                input.setTenantId(tenant.getTenantId());
                input.setDbType(tenant.getDbType());
                input.setConfiguration(tenant.getConfiguration());
                tenantDataSourceManageService.update(id, input);

                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to enable datasource: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean disable(String id) {
        try {
            DataSourceEntity global = dataSourceManageService.get(id);
            if (global != null) {
                checkPermission(null);
                dataSourceManageService.disable(id);
                return true;
            }
            TenantDataSourceEntity tenant = tenantDataSourceManageService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                UpsertTenantDataSource input = new UpsertTenantDataSource();
                input.setId(id);
                input.setEnabled(0);
                input.setDbName(tenant.getDbName());
                input.setTenantId(tenant.getTenantId());
                input.setDbType(tenant.getDbType());
                input.setConfiguration(tenant.getConfiguration());
                tenantDataSourceManageService.update(id, input);

                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to disable datasource: {}", id, e);
            return false;
        }
    }

    private void checkPermission(String targetTenantId) {
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(currentTenantId) || "default".equals(currentTenantId)) {
            // 管理员或无租户上下文，允许一切
            return;
        }

        if (targetTenantId == null) {
            // 普通租户试图操作全局数据源
            throw new SecurityException("Current tenant cannot operate on global datasource");
        }

        if (!currentTenantId.equals(targetTenantId)) {
            // 租户不匹配
            throw new SecurityException("Current tenant cannot operate on other tenant's datasource");
        }
    }

    @Override
    public void subscribe(String clientId, DataSourceChangeListener callback) {
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        callbackManager.addSubscriber(clientId, currentTenantId, callback);
    }

    @Override
    public void unsubscribe(String clientId) {
        callbackManager.removeSubscriber(clientId);
    }

    private RemoteDataSource toDTO(DataSourceEntity entity) {
        RemoteDataSource dto = new RemoteDataSource();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDatasourceName());
        dto.setDriverClassName(entity.getDriverClassName());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setPassword(entity.getPassword());
        dto.setEnabled(entity.getEnabled());
        dto.setVersion(System.currentTimeMillis()); // Set version
        return dto;
    }

    private RemoteDataSource toDTO(TenantDataSourceEntity entity) {
        RemoteDataSource dto = new RemoteDataSource();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDbName());
        dto.setEnabled(entity.getEnabled());
        dto.setTenantId(entity.getTenantId());
        dto.setDbType(entity.getDbType());
        dto.setVersion(System.currentTimeMillis()); // Set version

        try {
            if (StringUtils.hasText(entity.getConfiguration())) {
                JsonNode node = objectMapper.readTree(entity.getConfiguration());
                validAndSet(dto, node);
            }
        } catch (Exception e) {
            log.error("Failed to parse tenant configuration", e);
        }
        return dto;
    }

    public static void validAndSet(RemoteDataSource dto, JsonNode node) {
        if (node.has("jdbcUrl")) dto.setJdbcUrl(node.get("jdbcUrl").asText());
        else if (node.has("url")) dto.setJdbcUrl(node.get("url").asText());
        if (node.has("username")) dto.setUsername(node.get("username").asText());
        if (node.has("password")) dto.setPassword(node.get("password").asText());
        if (node.has("driverClassName"))
            dto.setDriverClassName(node.get("driverClassName").asText());
    }

    private UpsertDataSource toUpsertDataSource(RemoteDataSource dto) {
        return ConvertUtils.convert(dto);
    }

    private UpsertTenantDataSource toUpsertTenantDataSource(RemoteDataSource dto) {
        UpsertTenantDataSource input = new UpsertTenantDataSource();
        input.setId(dto.getId());
        input.setDbName(dto.getDatasourceName());
        input.setTenantId(dto.getTenantId());
        input.setEnabled(dto.getEnabled());

        if (StringUtils.hasText(dto.getDbType())) {
            input.setDbType(dto.getDbType());
        } else {
            input.setDbType("mysql");
        }

        try {
            var config = new Object() {
                @Getter
                public final String jdbcUrl = dto.getJdbcUrl();

                @Getter
                public final String username = dto.getUsername();

                @Getter
                public final String password = dto.getPassword();

                @Getter
                public final String driverClassName = dto.getDriverClassName();
            };
            input.setConfiguration(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            log.error("Failed to serialize tenant configuration", e);
        }
        return input;
    }
}
