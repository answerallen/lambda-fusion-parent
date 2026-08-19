package com.lambda.fusion.ai.runtime.state;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;

/**
 * 从 dynamic-datasource 主数据源中按名解析底层 {@link DataSource}，供 MySQL/Postgres 状态存储后端复用。
 * AgentScope 状态存储需直连底层 ds（而非路由数据源，否则跨线程取连接会回主库），故按名注入。
 *
 * @author Jin
 */
public final class StateStoreDataSources {

    private StateStoreDataSources() {}

    /**
     * 按名解析底层 DataSource；主源非 {@link DynamicRoutingDataSource} 或名称不存在时回退主源。
     *
     * @param primary 主 DataSource（fusion 下通常为 {@link DynamicRoutingDataSource}）
     * @param name dynamic-datasource 名称（如 {@code master} / {@code ai-postgres}）
     * @return 命名底层数据源；找不到时回退主源（可能为 {@code null}）
     */
    public static DataSource resolveNamed(DataSource primary, String name) {
        if (primary instanceof DynamicRoutingDataSource dds && StringUtils.isNotBlank(name)) {
            DataSource named = dds.getDataSource(name);
            if (named != null) {
                return named;
            }
        }
        return primary;
    }
}
