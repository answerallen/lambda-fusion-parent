package com.lambda.fusion.datasource.commons.event;

import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源变更事件
 *
 * @author Jin
 */
@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DataSourceEvent extends ApplicationEvent {

    private final RemoteDataSource dataSource;

    /** 变更类型，用于 DataSourceListener 精确映射广播事件的 ChangeType */
    private final DatasourceConstants.ChangeType changeType;

    /**
     * 内部构造，统一通过工厂方法创建
     */
    private DataSourceEvent(Object source, RemoteDataSource dataSource, DatasourceConstants.ChangeType changeType) {
        super(source);
        this.dataSource = dataSource;
        this.changeType = changeType;
    }

    /**
     * 新增数据源事件
     */
    public static DataSourceEvent add(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, DatasourceConstants.ChangeType.ADD);
    }

    /**
     * 更新数据源事件（配置变更、启用）
     */
    public static DataSourceEvent update(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, DatasourceConstants.ChangeType.UPDATE);
    }

    /**
     * 删除/禁用数据源事件
     */
    public static DataSourceEvent remove(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, DatasourceConstants.ChangeType.DELETE);
    }
}
