package com.lambda.fusion.datasource.server;

import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.datasource.api.DataSourceChangeListener;
import com.lambda.fusion.datasource.api.RemoteDataSourceApi;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 远程数据源服务Server端实现
 *
 * @author Jin
 */
@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ServerDataSourceService implements RemoteDataSourceApi {

    private final DataSourceManageService dataSourceManageService;
    private final DataSourceChangeDispatcher callbackManager;

    public ServerDataSourceService(
            DataSourceManageService dataSourceManageService, DataSourceChangeDispatcher callbackManager) {
        this.dataSourceManageService = dataSourceManageService;
        this.callbackManager = callbackManager;
    }

    @Override
    public List<RemoteDataSource> listAll() {
        return dataSourceManageService.listAll().stream()
                .map(this::toRemoteDataSource)
                .collect(Collectors.toList());
    }

    @Override
    public List<RemoteDataSource> listEnabled() {
        return dataSourceManageService.listAll().stream()
                .filter(e -> e.getStatus() != null
                        && Integer.valueOf(1).equals(e.getStatus().getCode()))
                .map(this::toRemoteDataSource)
                .collect(Collectors.toList());
    }

    @Override
    public RemoteDataSource get(String id) {
        // 先查全局
        DataSourceEntity global = dataSourceManageService.getById(id);
        if (global != null) {
            return toRemoteDataSource(global);
        }
        return null;
    }

    @Override
    public boolean add(RemoteDataSource remoteDataSource) {
        try {
            UpsertDataSource input = ConvertUtils.convert(remoteDataSource);
            dataSourceManageService.save(input);
            return true;
        } catch (Exception e) {
            log.error("Failed to add datasource: {}", remoteDataSource.getId(), e);
            return false;
        }
    }

    @Override
    public boolean update(String id, RemoteDataSource remoteDataSource) {
        try {
            UpsertDataSource input = toUpsertDataSource(remoteDataSource);
            input.setId(id);
            dataSourceManageService.update(id, input);
            remoteDataSource.setId(id);
            return true;
        } catch (Exception e) {
            log.error("Failed to update datasource: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            DataSourceEntity global = dataSourceManageService.getById(id);
            if (global != null) {
                checkPermission();
                dataSourceManageService.delete(id);
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
            DataSourceEntity global = dataSourceManageService.getById(id);
            if (global != null) {
                checkPermission();
                dataSourceManageService.enable(id);
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
            DataSourceEntity global = dataSourceManageService.getById(id);
            if (global != null) {
                checkPermission();
                dataSourceManageService.disable(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to disable datasource: {}", id, e);
            return false;
        }
    }

    private void checkPermission() {
        String currentTenantId = DubboContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(currentTenantId) || "default".equals(currentTenantId)) {
            // 管理员或无租户上下文，允许一切
            return;
        }

        // 普通租户试图操作全局数据源
        throw new SecurityException("Current tenant cannot operate on global datasource");
    }

    @Override
    public void subscribe(String clientId, DataSourceChangeListener callback) {
        callbackManager.addSubscriber(clientId, callback);
    }

    @Override
    public void unsubscribe(String clientId) {
        callbackManager.removeSubscriber(clientId);
    }

    private RemoteDataSource toRemoteDataSource(DataSourceEntity entity) {
        RemoteDataSource remoteDataSource = ConvertUtils.convert(entity);
        remoteDataSource.setVersion(System.currentTimeMillis());
        return remoteDataSource;
    }

    private UpsertDataSource toUpsertDataSource(RemoteDataSource dto) {
        return ConvertUtils.convert(dto);
    }
}
