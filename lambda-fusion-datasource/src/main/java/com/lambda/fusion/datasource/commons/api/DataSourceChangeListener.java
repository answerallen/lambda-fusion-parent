package com.lambda.fusion.datasource.commons.api;

/**
 * 数据源变更回调接口
 * <p>
 * Client端实现此接口，Server端在数据源变更时调用。
 * </p>
 */
public interface DataSourceChangeListener {

    /**
     * 数据源变更通知
     *
     * @param event 变更事件
     */
    void onDataSourceChanged(DataSourceChangeEvent event);
}
