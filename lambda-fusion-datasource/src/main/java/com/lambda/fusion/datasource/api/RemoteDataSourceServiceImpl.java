package com.lambda.fusion.datasource.api;

import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.dispatcher.DataSourceChangeDispatcher;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
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
    private final DataSourceChangeDispatcher callbackManager;

    @Override
    public List<RemoteDataSource> listAll() {
        return dataSourceManageService.listAll().stream()
                .map(this::toRemoteDataSource)
                .collect(Collectors.toList());
    }

    @Override
    public List<RemoteDataSource> listEnabled() {
        return dataSourceManageService.listAll().stream()
                .filter(e -> DatasourceConstants.DatasourceStatus.ONLINE == e.getStatus())
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
            DataSourceEntity dataSourceEntity = dataSourceManageService.getById(id);
            if (dataSourceEntity != null) {
                DataSourceChangeEvent event = new DataSourceChangeEvent();
                event.setChangeType(DatasourceConstants.ChangeType.INIT_SCHEMA);
                event.setDataSourceId(id);
                event.setDataSource(toRemoteDataSource(dataSourceEntity));
                event.setTimestamp(System.currentTimeMillis());
                callbackManager.broadcast(event);
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
            DataSourceEntity dataSourceEntity = dataSourceManageService.getById(id);
            if (dataSourceEntity != null) {
                DataSourceChangeEvent event = new DataSourceChangeEvent();
                event.setChangeType(DatasourceConstants.ChangeType.REMOVE_SCHEMA);
                event.setDataSourceId(id);
                event.setDataSource(toRemoteDataSource(dataSourceEntity));
                event.setTimestamp(System.currentTimeMillis());
                callbackManager.broadcast(event);
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

    private UpsertDataSource toUpsertDataSource(RemoteDataSource dto) {
        return ConvertUtils.convert(dto);
    }
}
