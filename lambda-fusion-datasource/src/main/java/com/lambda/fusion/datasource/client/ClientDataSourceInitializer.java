package com.lambda.fusion.datasource.client;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.api.dto.RemoteDataSourceDTO;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 客户端模式初始化器 - 从远程Dubbo服务加载数据源并订阅
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lambda.fusion.datasource.mode", havingValue = "client")
@RequiredArgsConstructor
public class ClientDataSourceInitializer implements ApplicationRunner {

    @DubboReference(version = "1.0.0", group = "datasource", check = false)
    private RemoteDataSourceService remoteDataSourceService;

    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSourceChangeCallbackImpl callback;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting dynamic datasource initialization (Client Mode)...");
        
        try {
            // 1. 获取初始列表
            List<RemoteDataSourceDTO> dataSources = remoteDataSourceService.listEnabled();
            if (dataSources != null) {
                log.info("Fetched {} remote datasources.", dataSources.size());
                for (RemoteDataSourceDTO dto : dataSources) {
                    try {
                        DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(dto);
                        dynamicDataSourceService.addDataSource(property);
                        log.info("Loaded remote datasource: {}", dto.getDatasourceName());
                    } catch (Exception e) {
                        log.error("Failed to load remote datasource: {}", dto.getDatasourceName(), e);
                    }
                }
            } else {
                log.info("No remote datasources fetched.");
            }
            
            // 2. 订阅变更
            String clientId = generateClientId();
            remoteDataSourceService.subscribe(clientId, callback);
            log.info("Subscribed to remote datasource changes. ClientId: {}", clientId);
            
            // 添加关闭钩子取消订阅
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    remoteDataSourceService.unsubscribe(clientId);
                    log.info("Unsubscribed datasource changes.");
                } catch (Exception e) {
                    log.warn("Failed to unsubscribe", e);
                }
            }));
            
        } catch (Exception e) {
            log.error("Failed to initialize remote datasources", e);
        }
    }
    
    private String generateClientId() {
        try {
            return InetAddress.getLocalHost().getHostAddress() + ":" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "unknown:" + UUID.randomUUID().toString();
        }
    }
}
