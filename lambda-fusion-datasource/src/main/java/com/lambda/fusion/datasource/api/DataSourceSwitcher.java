package com.lambda.fusion.datasource.api;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源切换器（通用组件）
 * <p>
 * 提供编程式数据源切换能力，支持 try-with-resources 自动恢复。
 * 使用 baomidou 的 DynamicDataSourceContextHolder 管理上下文。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 切换到指定数据源
 * try (DataSourceSwitcher switcher = DataSourceSwitcher.switchTo("tenant-db-1001")) {
 *     // 执行数据库操作
 *     userMapper.selectList(null);
 * } // 自动恢复到之前的数据源
 * </pre>
 */
@Slf4j
public class DataSourceSwitcher implements AutoCloseable {

    private final String previousDataSource;

    private DataSourceSwitcher(String targetDataSource) {
        this.previousDataSource = DynamicDataSourceContextHolder.peek();
        DynamicDataSourceContextHolder.push(targetDataSource);
        log.debug("Switched datasource from {} to {}", previousDataSource, targetDataSource);
    }

    /**
     * 切换到指定数据源
     *
     * @param dataSourceName 目标数据源名称
     * @return DataSourceSwitcher 实例，用于 try-with-resources
     */
    public static DataSourceSwitcher switchTo(String dataSourceName) {
        return new DataSourceSwitcher(dataSourceName);
    }

    @Override
    public void close() {
        DynamicDataSourceContextHolder.poll();
        log.debug("Restored datasource context");
    }
}
