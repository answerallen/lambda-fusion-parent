package com.lambda.fusion.datasource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.cloud.core.convert.ConverterResolver;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.api.callback.DataSourceChangeCallback;
import com.lambda.fusion.datasource.api.callback.DataSourceChangeEvent;
import com.lambda.fusion.datasource.api.callback.DataSourceChangeEvent.ChangeType;
import com.lambda.fusion.datasource.api.dto.RemoteDataSourceDTO;
import com.lambda.fusion.datasource.manager.DataSourceCallbackManager;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

/**
 * 远程数据源服务Server端实现
 */
@Slf4j
@DubboService(version = "1.0.0", group = "datasource")
@RequiredArgsConstructor
public class RemoteDataSourceServiceImpl implements RemoteDataSourceService {

    private final DataSourceManageService globalService;
    private final TenantDataSourceManageService tenantService;
    private final DataSourceCallbackManager callbackManager;
    private final ObjectMapper objectMapper;

    @Override
    public List<RemoteDataSourceDTO> listAll() {
        // 1. 全局数据源
        List<RemoteDataSourceDTO> globals = globalService.listAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        // 2. 当前租户数据源 (如果有上下文)
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(currentTenantId) && !"default".equals(currentTenantId)) {
             List<RemoteDataSourceDTO> tenants = tenantService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId))
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
             globals.addAll(tenants);
        }
        return globals;
    }

    @Override
    public List<RemoteDataSourceDTO> listEnabled() {
         // 1. 全局已启用数据源
        List<RemoteDataSourceDTO> globals = globalService.listAll().stream()
                .filter(e -> e.getEnabled() != null && e.getEnabled() == 1)
                .map(this::toDTO)
                .collect(Collectors.toList());

        // 2. 当前租户已启用数据源
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(currentTenantId) && !"default".equals(currentTenantId)) {
            List<RemoteDataSourceDTO> tenants = tenantService
                    .list(Wrappers.<TenantDataSourceEntity>lambdaQuery()
                            .eq(TenantDataSourceEntity::getTenantId, currentTenantId)
                            .eq(TenantDataSourceEntity::getEnabled, 1))
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
            globals.addAll(tenants);
        }
        return globals;
    }

    @Override
    public RemoteDataSourceDTO get(String id) {
        // 先查全局
        DataSourceEntity global = globalService.get(id);
        if (global != null) {
            return toDTO(global);
        }
        // 再查租户
        TenantDataSourceEntity tenant = tenantService.get(id);
        if (tenant != null) {
            // 校验租户权限
             String currentTenantId = DubboContextHolder.getCurrentTenantId();
             if (!StringUtils.hasText(currentTenantId) || "default".equals(currentTenantId) || currentTenantId.equals(tenant.getTenantId())) {
                 return toDTO(tenant);
             }
        }
        return null;
    }

    @Override
    public boolean add(RemoteDataSourceDTO dto) {
        try {
            if (StringUtils.hasText(dto.getTenantId())) {
                UpsertTenantDataSource input = toUpsertTenant(dto);
                tenantService.save(input);
                broadcastEvent(ChangeType.ADD, dto.getId(), dto.getTenantId(), dto);
            } else {
                UpsertDataSource input = toUpsert(dto);
                globalService.save(input);
                broadcastEvent(ChangeType.ADD, dto.getId(), null, dto);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to add datasource: {}", dto.getId(), e);
            return false;
        }
    }

    @Override
    public boolean update(String id, RemoteDataSourceDTO dto) {
        try {
            // Determine if global or tenant based on DTO or lookup
            // Assuming tenantId in DTO is authoritative for update
            if (StringUtils.hasText(dto.getTenantId())) {
                UpsertTenantDataSource input = toUpsertTenant(dto);
                input.setId(id); // Ensure ID is set for update
                tenantService.update(id, input);
                dto.setId(id);
                broadcastEvent(ChangeType.UPDATE, id, dto.getTenantId(), dto);
            } else {
                UpsertDataSource input = toUpsert(dto);
                input.setId(id); // Ensure ID is set for update
                globalService.update(id, input);
                dto.setId(id);
                broadcastEvent(ChangeType.UPDATE, id, null, dto);
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
            // Check global first
            DataSourceEntity global = globalService.get(id);
            if (global != null) {
                globalService.delete(id);
                broadcastEvent(ChangeType.DELETE, id, null, toDTO(global));
                return true;
            }
            
            // Check tenant
            TenantDataSourceEntity tenant = tenantService.get(id);
            if (tenant != null) {
                tenantService.delete(id);
                broadcastEvent(ChangeType.DELETE, id, tenant.getTenantId(), toDTO(tenant));
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
        // Global only for now as Tenant service doesn't have test
        return globalService.test(id);
    }

    @Override
    public boolean enable(String id) {
        try {
            DataSourceEntity global = globalService.get(id);
            if (global != null) {
                globalService.enable(id);
                global.setEnabled(1); // Update object for broadcast
                broadcastEvent(ChangeType.ENABLE, id, null, toDTO(global));
                return true;
            }
            // Tenant enable/disable not explicitly in service interface, assuming update is needed or ignoring
            // If needed, we would fetch, set enabled, and update.
             TenantDataSourceEntity tenant = tenantService.get(id);
             if (tenant != null) {
                 UpsertTenantDataSource input = new UpsertTenantDataSource();
                 input.setId(id);
                 input.setEnabled(1);
                 input.setDbName(tenant.getDbName());
                 input.setTenantId(tenant.getTenantId());
                 input.setDbType(tenant.getDbType());
                 input.setConfiguration(tenant.getConfiguration());
                 tenantService.update(id, input);
                 
                 tenant.setEnabled(1);
                 broadcastEvent(ChangeType.ENABLE, id, tenant.getTenantId(), toDTO(tenant));
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
            DataSourceEntity global = globalService.get(id);
            if (global != null) {
                globalService.disable(id);
                broadcastEvent(ChangeType.DISABLE, id, null, toDTO(global));
                return true;
            }
             TenantDataSourceEntity tenant = tenantService.get(id);
             if (tenant != null) {
                 UpsertTenantDataSource input = new UpsertTenantDataSource();
                 input.setId(id);
                 input.setEnabled(0);
                 input.setDbName(tenant.getDbName());
                 input.setTenantId(tenant.getTenantId());
                 input.setDbType(tenant.getDbType());
                 input.setConfiguration(tenant.getConfiguration());
                 tenantService.update(id, input);
                 
                 broadcastEvent(ChangeType.DISABLE, id, tenant.getTenantId(), toDTO(tenant));
                 return true;
             }
            return false;
        } catch (Exception e) {
            log.error("Failed to disable datasource: {}", id, e);
            return false;
        }
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

    private void broadcastEvent(ChangeType type, String dataSourceId,
                                String tenantId, RemoteDataSourceDTO dto) {
        DataSourceChangeEvent event = new DataSourceChangeEvent();
        event.setChangeType(type);
        event.setDataSourceId(dataSourceId);
        event.setTenantId(tenantId);
        event.setDataSource(dto);
        event.setTimestamp(System.currentTimeMillis());

        callbackManager.broadcast(event);
    }

    private RemoteDataSourceDTO toDTO(DataSourceEntity entity) {
        RemoteDataSourceDTO dto = new RemoteDataSourceDTO();
        dto.setId(entity.getId());
        dto.setDatasourceName(entity.getDatasourceName());
        dto.setDriverClassName(entity.getDriverClassName());
        dto.setJdbcUrl(entity.getJdbcUrl());
        dto.setUsername(entity.getUsername());
        dto.setPassword(entity.getPassword());
        dto.setEnabled(entity.getEnabled());
        return dto;
    }
    
    private RemoteDataSourceDTO toDTO(TenantDataSourceEntity entity) {
        // Reuse logic from previous implementation but adapted for DTO
         RemoteDataSourceDTO dto = new RemoteDataSourceDTO();
         dto.setId(entity.getId());
         dto.setDatasourceName(entity.getDbName());
         dto.setEnabled(entity.getEnabled());
         dto.setTenantId(entity.getTenantId());
         dto.setDbType(entity.getDbType());
         
         try {
            if (StringUtils.hasText(entity.getConfiguration())) {
                JsonNode node = objectMapper.readTree(entity.getConfiguration());
                if (node.has("jdbcUrl")) dto.setJdbcUrl(node.get("jdbcUrl").asText());
                else if (node.has("url")) dto.setJdbcUrl(node.get("url").asText());
                
                if (node.has("username")) dto.setUsername(node.get("username").asText());
                if (node.has("password")) dto.setPassword(node.get("password").asText());
                if (node.has("driverClassName")) dto.setDriverClassName(node.get("driverClassName").asText());
            }
        } catch (Exception e) {
            log.error("Failed to parse tenant configuration", e);
        }
        return dto;
    }

    private UpsertDataSource toUpsert(RemoteDataSourceDTO dto) {
        UpsertDataSource input = new UpsertDataSource();
        input.setId(dto.getId()); // Set ID
        input.setDatasourceName(dto.getDatasourceName());
        input.setDriverClassName(dto.getDriverClassName());
        input.setJdbcUrl(dto.getJdbcUrl());
        input.setUsername(dto.getUsername());
        input.setPassword(dto.getPassword());
        input.setEnabled(dto.getEnabled());
        return input;
    }
    
    private UpsertTenantDataSource toUpsertTenant(RemoteDataSourceDTO dto) {
        UpsertTenantDataSource input = new UpsertTenantDataSource();
        input.setId(dto.getId()); // Set ID
        input.setDbName(dto.getDatasourceName());
        input.setTenantId(dto.getTenantId());
        input.setEnabled(dto.getEnabled());
        
        // Handle dbType
        if (StringUtils.hasText(dto.getDbType())) {
            input.setDbType(dto.getDbType());
        } else {
            // Try to infer or set default
            input.setDbType("mysql"); // Default to mysql if unknown, or handle appropriately
        }
        
        // Configuration needs to be built from fields
        // Simple JSON construction
        try {
            // Creating a simple object to serialize
            var config = new Object() {
                public String jdbcUrl = dto.getJdbcUrl();
                public String username = dto.getUsername();
                public String password = dto.getPassword();
                public String driverClassName = dto.getDriverClassName();
            };
            input.setConfiguration(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
             log.error("Failed to serialize tenant configuration", e);
        }
        return input;
    }
}
