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
    private final boolean isRemove;

    public DataSourceEvent(Object source, RemoteDataSource dataSource, boolean isRemove) {
        super(source);
        this.dataSource = dataSource;
        this.isRemove = isRemove;
    }

    public static DataSourceEvent update(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, false);
    }

    public static DataSourceEvent remove(Object source, RemoteDataSource dataSource) {
        return new DataSourceEvent(source, dataSource, true);
    }
}
