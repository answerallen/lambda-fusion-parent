package com.lambda.fusion.datasource.event;

import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源变更事件
 *
 * @author Jin
 */
@Getter
public class DataSourceChangeEvent extends ApplicationEvent {

    private final RemoteDataSource dataSource;
    private final boolean isRemove;

    public DataSourceChangeEvent(Object source, RemoteDataSource dataSource, boolean isRemove) {
        super(source);
        this.dataSource = dataSource;
        this.isRemove = isRemove;
    }

    public static DataSourceChangeEvent update(Object source, RemoteDataSource dataSource) {
        return new DataSourceChangeEvent(source, dataSource, false);
    }

    public static DataSourceChangeEvent remove(Object source, RemoteDataSource dataSource) {
        return new DataSourceChangeEvent(source, dataSource, true);
    }
}
