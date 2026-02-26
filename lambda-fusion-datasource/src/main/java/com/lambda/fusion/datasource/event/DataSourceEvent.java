package com.lambda.fusion.datasource.event;

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
    private final ChangeType changeType;

    /**
     * 内部构造，统一通过工厂方法创建
     */
    private DataSourceEvent(Object source, RemoteDataSource dataSource, ChangeType changeType) {
        super(source);
        this.dataSource = dataSource;
        this.changeType = changeType;
    }

    /**
     * 新增数据源事件
     */
    public static DataSourceEvent add(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, ChangeType.ADD);
    }

    /**
     * 更新数据源事件（配置变更、启用）
     */
    public static DataSourceEvent update(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, ChangeType.UPDATE);
    }

    /**
     * 删除/禁用数据源事件
     */
    public static DataSourceEvent remove(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, ChangeType.REMOVE);
    }

    /**
     * @deprecated 使用 {@link #getChangeType()} 代替，保留此方法以兼容现有调用
     */
    @Deprecated
    public boolean isRemove() {
        return changeType == ChangeType.REMOVE;
    }
}
