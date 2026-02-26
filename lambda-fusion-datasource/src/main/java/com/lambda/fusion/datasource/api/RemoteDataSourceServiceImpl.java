package com.lambda.fusion.datasource.api;

import cn.hutool.core.codec.Base64;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.model.*;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.service.TenantDataSourceService;
import com.lambda.fusion.datasource.tenant.TenantIsolationModeResolver;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.stream.Collectors;
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
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class RemoteDataSourceServiceImpl implements RemoteDataSourceService {

    private final DataSourceManageService dataSourceManageService;
    private final TenantDataSourceService tenantDataSourceService;
    private final DataSourceChangeDispatcher callbackManager;
    private final ObjectMapper objectMapper;
    private final TenantIsolationModeResolver tenantIsolationModeResolver;

    @Override
    public List<RemoteDataSource> listAll() {
        // 1. 全局数据源
        List<RemoteDataSource> globals = dataSourceManageService.listAll().stream()
                .map(this::toRemoteDataSource)
                .collect(Collectors.toList());

        // 2. 当前租户数据源 (如果有上下文)
        String currentTenantId = resolveCurrentTenantId();
        if (StringUtils.hasText(currentTenantId)
                && !"default".equals(currentTenantId)
                && shouldIncludeTenantDataSources(currentTenantId)) {
            List<RemoteDataSource> tenants = tenantDataSourceService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId))
                    .stream()
                    .map(this::toRemoteDataSource)
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
                .map(this::toRemoteDataSource)
                .collect(Collectors.toList());

        // 2. 当前租户已启用数据源
        String currentTenantId = resolveCurrentTenantId();
        if (StringUtils.hasText(currentTenantId)
                && !"default".equals(currentTenantId)
                && shouldIncludeTenantDataSources(currentTenantId)) {
            List<RemoteDataSource> tenants = tenantDataSourceService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId)
                            .eq(TenantDataSourceEntity::getEnabled, 1))
                    .stream()
                    .map(this::toRemoteDataSource)
                    .toList();
            globals.addAll(tenants);
        }
        return globals;
    }

    private boolean shouldIncludeTenantDataSources(String tenantId) {
        return !tenantIsolationModeResolver.isShared(tenantId);
    }

    private String resolveCurrentTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return DubboContextHolder.getCurrentTenantId();
    }

    @Override
    public RemoteDataSource get(String id) {
        // 先查全局
        DataSourceEntity global = dataSourceManageService.get(id);
        if (global != null) {
            return toRemoteDataSource(global);
        }
        // 再查租户
        TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
        if (tenant != null) {
            // 校验租户权限
            String currentTenantId = resolveCurrentTenantId();
            if (!StringUtils.hasText(currentTenantId)
                    || "default".equals(currentTenantId)
                    || currentTenantId.equals(tenant.getTenantId())) {
                return toRemoteDataSource(tenant);
            }
        }
        return null;
    }

    @Override
    public boolean add(RemoteDataSource remoteDataSource) {
        try {
            if (StringUtils.hasText(remoteDataSource.getTenantId())) {
                checkPermission(remoteDataSource.getTenantId());
                UpsertTenantDataSource input = toUpsertTenantDataSource(remoteDataSource);
                tenantDataSourceService.save(input);
            } else {
                checkPermission(null);
                UpsertDataSource input = toUpsertDataSource(remoteDataSource);
                dataSourceManageService.save(input);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to add datasource: {}", remoteDataSource.getId(), e);
            return false;
        }
    }

    @Override
    public boolean update(String id, RemoteDataSource remoteDataSource) {
        try {
            if (StringUtils.hasText(remoteDataSource.getTenantId())) {
                checkPermission(remoteDataSource.getTenantId());
                UpsertTenantDataSource input = toUpsertTenantDataSource(remoteDataSource);
                input.setId(id);
                tenantDataSourceService.update(id, input);
                remoteDataSource.setId(id);
            } else {
                checkPermission(null);
                UpsertDataSource input = toUpsertDataSource(remoteDataSource);
                input.setId(id);
                dataSourceManageService.update(id, input);
                remoteDataSource.setId(id);
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

            TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                tenantDataSourceService.delete(id);
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
            TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                UpsertTenantDataSource input = new UpsertTenantDataSource();
                input.setId(id);
                input.setEnabled(1);
                input.setDbName(tenant.getDbName());
                input.setTenantId(tenant.getTenantId());
                input.setDbType(tenant.getDbType());
                input.setConfiguration(tenant.getConfiguration());
                tenantDataSourceService.update(id, input);

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
            TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                UpsertTenantDataSource upsertTenantDataSource = new UpsertTenantDataSource();
                upsertTenantDataSource.setId(id);
                upsertTenantDataSource.setEnabled(0);
                upsertTenantDataSource.setDbName(tenant.getDbName());
                upsertTenantDataSource.setTenantId(tenant.getTenantId());
                upsertTenantDataSource.setDbType(tenant.getDbType());
                upsertTenantDataSource.setConfiguration(tenant.getConfiguration());
                tenantDataSourceService.update(id, upsertTenantDataSource);

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
    public void subscribe(String clientId, String tenantId, DataSourceChangeListener callback) {
        callbackManager.addSubscriber(clientId, tenantId, callback);
    }

    @Override
    public void unsubscribe(String clientId) {
        callbackManager.removeSubscriber(clientId);
    }

    @Override
    public boolean initSchema(String id) {
        try {
            TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                tenantDataSourceService.initSchema(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to init schema: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean removeSchema(String id) {
        try {
            TenantDataSourceEntity tenant = tenantDataSourceService.get(id);
            if (tenant != null) {
                checkPermission(tenant.getTenantId());
                tenantDataSourceService.removeSchema(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to remove schema: {}", id, e);
            return false;
        }
    }

    private RemoteDataSource toRemoteDataSource(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = DataSourcePropertyUtils.buildDataSourceEntity(entity);
        remoteDataSource.setVersion(System.currentTimeMillis());
        return remoteDataSource;
    }

    private RemoteDataSource toRemoteDataSource(TenantDataSourceEntity entity) {
        RemoteDataSource remoteDataSource = new RemoteDataSource();
        remoteDataSource.setId(entity.getId());
        remoteDataSource.setDatasourceName(entity.getDbName());
        remoteDataSource.setEnabled(entity.getEnabled().getCode());
        remoteDataSource.setTenantId(entity.getTenantId());
        remoteDataSource.setDbType(entity.getDbType());
        remoteDataSource.setVersion(System.currentTimeMillis());

        try {
            if (StringUtils.hasText(entity.getConfiguration())) {
                JsonNode node = objectMapper.readTree(entity.getConfiguration());
                validAndSet(remoteDataSource, node);
            }
        } catch (Exception e) {
            log.error("Failed to parse tenant configuration", e);
        }

        // 由于 validAndSet 直接从 JSON 解析获取明文密码，此处再将其统一进行 Base64 编码
        if (remoteDataSource.getPassword() != null) {
            remoteDataSource.setPassword(Base64.encode(remoteDataSource.getPassword()));
        }

        return remoteDataSource;
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

    private UpsertTenantDataSource toUpsertTenantDataSource(RemoteDataSource remoteDataSource) {
        UpsertTenantDataSource input = new UpsertTenantDataSource();
        input.setId(remoteDataSource.getId());
        input.setDbName(remoteDataSource.getDatasourceName());
        input.setTenantId(remoteDataSource.getTenantId());
        input.setEnabled(remoteDataSource.getEnabled());

        if (StringUtils.hasText(remoteDataSource.getDbType())) {
            input.setDbType(remoteDataSource.getDbType());
        } else {
            input.setDbType("mysql");
        }

        try {
            input.setConfiguration(objectMapper.writeValueAsString(remoteDataSource));
        } catch (Exception e) {
            log.error("Failed to serialize tenant configuration", e);
        }
        return input;
    }
}
