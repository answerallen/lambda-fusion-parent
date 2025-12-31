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
public class LocalDataSourceChangeEvent extends ApplicationEvent {

    private final RemoteDataSource dataSource;
    private final boolean isRemove;

    public LocalDataSourceChangeEvent(Object source, RemoteDataSource dataSource, boolean isRemove) {
        super(source);
        this.dataSource = dataSource;
        this.isRemove = isRemove;
    }

    public static LocalDataSourceChangeEvent update(Object source, RemoteDataSource dataSource) {
        return new LocalDataSourceChangeEvent(source, dataSource, false);
    }

    public static LocalDataSourceChangeEvent remove(Object source, RemoteDataSource dataSource) {
        return new LocalDataSourceChangeEvent(source, dataSource, true);
    }
}
