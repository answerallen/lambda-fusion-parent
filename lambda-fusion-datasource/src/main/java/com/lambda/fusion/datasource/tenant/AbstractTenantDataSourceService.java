package com.lambda.fusion.datasource.tenant;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.proxy.TenantDataSourceProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 租户数据源服务抽象基类
 * <p>
 * 提供租户数据源创建、初始化和删除的通用流程。
 * 子类需要实现特定模块的配置和 Schema 初始化逻辑。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Service
 * public class AiTenantDataSourceService extends AbstractTenantDataSourceService {
 *
 *     private final AiSchemaInitializer aiSchemaInitializer;
 *     private final AiProperties aiProperties;
 *
 *     &#64;Override
 *     protected TenantSchemaInitializer getSchemaInitializer() {
 *         return aiSchemaInitializer;
 *     }
 *
 *     &#64;Override
 *     protected String getModule() {
 *         return "ai";
 *     }
 * }
 * </pre>
 *
 * @author Lambda
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTenantDataSourceService {

    private final TenantDataSourceManager tenantDataSourceManager;
    private final DynamicRoutingDataSource dynamicRoutingDataSource;


    /**
     * 获取 Schema 初始化器
     * <p>
     * 子类必须实现此方法，返回特定模块的 Schema 初始化器。
     * </p>
     *
     * @return Schema 初始化器
     */
    protected abstract TenantSchemaInitializer getSchemaInitializer();

    /**
     * 获取模块标识
     * <p>
     * 子类必须实现此方法，返回特定模块的标识符。
     * 例如：AI 模块返回 "ai"，Authority 模块返回 "auth"
     * </p>
     *
     * @return 模块标识符
     */
    protected abstract String getTenantPrefix();

    /**
     * 创建租户数据源并初始化 Schema
     * <p>
     * 完整的租户配置流程：
     * 1. 检查租户数据源是否已存在
     * 2. 创建租户数据源
     * 3. 初始化 Schema（调用子类提供的初始化器）
     * 4. 如果失败，自动回滚（删除已创建的数据源）
     * </p>
     *
     * @param tenantId         租户ID
     * @param dataSourceConfig 数据源配置
     * @throws Exception 如果创建或初始化失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void provisionTenant(String tenantId, RemoteDataSource dataSourceConfig) throws Exception {
        log.info("开始为租户 [{}] 配置数据源，前缀: {}", tenantId, getTenantPrefix());

        try {
            // 1. 检查租户数据源是否已存在
            if (tenantDataSourceExists(tenantId)) {
                log.warn("租户 [{}] 的数据源已存在", tenantId);
                throw new IllegalStateException("租户数据源已存在: " + tenantId);
            }

            // 2. 创建租户数据源
            boolean created = createTenantDataSource(tenantId, dataSourceConfig);
            if (!created) {
                throw new RuntimeException("为租户 [" + tenantId + "] 创建数据源失败");
            }

            log.info("租户 [{}] 的数据源创建成功", tenantId);

            // 3. 初始化 Schema
            try {
                DataSource dataSource = getDataSourceForTenant(tenantId);
                TenantSchemaInitializer schemaInitializer = getSchemaInitializer();

                if (schemaInitializer != null) {
                    schemaInitializer.initializeSchema(tenantId, dataSource);
                    log.info("租户 [{}] 的 Schema 初始化成功", tenantId);
                } else {
                    log.warn("未提供 Schema 初始化器，跳过租户 [{}] 的 Schema 初始化", tenantId);
                }

            } catch (Exception e) {
                log.error("为租户 [{}] 初始化 Schema 失败，正在回滚数据源创建", tenantId, e);

                // 回滚：删除已创建的数据源
                try {
                    deleteTenantDataSource(tenantId);
                    log.info("已回滚租户 [{}] 的数据源", tenantId);
                } catch (Exception rollbackException) {
                    log.error("为租户 [{}] 回滚数据源失败", tenantId, rollbackException);
                }

                throw new RuntimeException("为租户 [" + tenantId + "] 初始化 Schema 失败", e);
            }

            log.info("租户 [{}] 配置完成", tenantId);

        } catch (Exception e) {
            log.error("租户 [{}] 配置失败", tenantId, e);
            throw e;
        }
    }

    /**
     * 删除租户数据源
     *
     * @param tenantId 租户ID
     * @return true 如果删除成功
     */
    public boolean deprovisionTenant(String tenantId) {
        log.info("开始为租户 [{}] 注销数据源，前缀: {}", tenantId, getTenantPrefix());

        try {
            // 检查租户数据源是否存在
            if (!tenantDataSourceExists(tenantId)) {
                log.warn("租户 [{}] 的数据源不存在", tenantId);
                return false;
            }

            // 删除租户数据源
            boolean deleted = deleteTenantDataSource(tenantId);

            if (deleted) {
                log.info("租户 [{}] 注销成功", tenantId);
            } else {
                log.warn("租户 [{}] 注销失败", tenantId);
            }

            return deleted;

        } catch (Exception e) {
            log.error("租户 [{}] 注销失败", tenantId, e);
            return false;
        }
    }

    /**
     * 检查租户数据源是否存在
     *
     * @param tenantId 租户ID
     * @return true 如果存在
     */
    public boolean isTenantProvisioned(String tenantId) {
        return tenantDataSourceExists(tenantId);
    }

    /**
     * 获取租户数据源名称
     *
     * @param tenantId 租户ID
     * @return 数据源名称
     */
    public String getTenantDataSourceName(String tenantId) {
        return tenantDataSourceManager.getTenantDataSourceName(tenantId, getTenantPrefix());
    }

    /**
     * 检查租户数据源是否存在
     *
     * @param tenantId 租户ID
     * @return true 如果存在
     */
    protected boolean tenantDataSourceExists(String tenantId) {
        return tenantDataSourceManager.tenantDataSourceExists(tenantId, getTenantPrefix());
    }

    /**
     * 创建租户数据源
     *
     * @param tenantId         租户ID
     * @param dataSourceConfig 数据源配置
     * @return true 如果创建成功
     */
    protected boolean createTenantDataSource(String tenantId, RemoteDataSource dataSourceConfig) {
        return tenantDataSourceManager.createTenantDataSource(tenantId, getTenantPrefix(), dataSourceConfig);
    }

    /**
     * 删除租户数据源
     *
     * @param tenantId 租户ID
     * @return true 如果删除成功
     */
    protected boolean deleteTenantDataSource(String tenantId) {
        return tenantDataSourceManager.deleteTenantDataSource(tenantId, getTenantPrefix());
    }

    /**
     * 获取租户数据源实例
     *
     * @param tenantId 租户ID
     * @return DataSource 实例
     */
    protected DataSource getDataSourceForTenant(String tenantId) {
        String dsName = getTenantDataSourceName(tenantId);
        return new TenantDataSourceProxy(dsName, tenantDataSourceManager, getTenantPrefix(), dynamicRoutingDataSource);
    }
}
