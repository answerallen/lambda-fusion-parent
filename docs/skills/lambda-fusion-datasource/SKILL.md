---
name: "lambda-fusion-datasource"
description: "数据源管理与多租户数据源订阅模块（server/client 双模式、Dubbo 分发、租户绑定与初始化、管理 API）。当需求涉及动态数据源管理或租户库编排时调用。"
---

# Lambda Fusion Datasource

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-datasource</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/autoconfig/DatasourceAutoConfiguration.java`
- 模块装配类：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/DatasourceConfigure.java`
- 配置属性：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/DatasourceProperties.java`
- AutoConfiguration imports：`lambda-fusion-datasource/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Liquibase 变更脚本：`lambda-fusion-datasource/src/main/resources/META-INF/db/changelogs/lambda-datasource-changelog.xml`

## 配置
配置前缀是 `lambda.fusion.datasource`。当前关键配置包括：
- `mode`
- `default-tenant-prefix`
- `dubbo.group`
- `dubbo.version`
- `retry.max-attempts`
- `retry.initial-delay`
- `retry.multiplier`
- `retry.max-delay`

## 主要能力
- server/client 双模式动态数据源管理
- server 模式下本地数据源装载与 Dubbo 对外暴露
- client 模式下远程全量拉取、变更订阅和失败重试初始化
- 数据源管理 API：增删改查、启停、连接测试
- 租户数据源绑定、绑定状态汇总、租户主库初始化
- Web 请求阶段的数据源切换拦截

## 入口定位
- 配置常量：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/DatasourceConstants.java`
- 配置属性：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/DatasourceProperties.java`
- REST 控制器：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/controller/DataSourceController.java`
- 管理服务：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/service/impl/DataSourceManageServiceImpl.java`
- Dubbo 接口：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/api/RemoteDataSourceApi.java`
- 变更回调接口：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/api/DataSourceChangeListener.java`
- 变更事件：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/dispatcher/DataSourceChangeEvent.java`
- server 初始化：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/server/ServerDataSourceInitializer.java`
- client 初始化：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/client/ClientDataSourceInitializer.java`
- 请求拦截器：`lambda-fusion-datasource/src/main/java/com/lambda/fusion/datasource/interceptor/TenantDataSourceInterceptor.java`

## 关键机制
- `mode=server` 时默认装配本地管理与 Dubbo 服务暴露；`mode=client` 时装配远程订阅初始化器和回调监听器
- client 初始化异步执行，使用指数退避重试拉取启用数据源并订阅变更
- 变更通知通过 `RemoteDataSourceApi.subscribe` + `DataSourceChangeListener` 回调完成
- `TenantDataSourceInterceptor` 执行顺序在 authority 模块租户上下文拦截器之后，用于基于租户上下文切换数据源
- 租户主库初始化和 schema 清理由 `tenant` 子包组件负责

## 条件装配说明
- `mode` 默认是 `server`
- `RemoteDataSourceApi` 服务 Bean 和 `ServerDataSourceInitializer` 仅在 server 模式装配
- `ClientDataSourceChangeListener` 与 `ClientDataSourceInitializer` 仅在 client 模式装配
- Dubbo `ServiceBean` 暴露依赖 Dubbo 类路径条件
- 部分 server 能力依赖 `DynamicDataSourceService`，租户 schema 初始化器/清理器通过 `ObjectProvider` 按需接入

## 常见改造入口
1. 调整 server/client 模式行为时，优先检查 `DatasourceConfigure`、`DatasourceConstants`、`DatasourceProperties`。
2. 调整 client 远程订阅、重试、全量装载时，检查 `ClientDataSourceInitializer`、`ClientDataSourceChangeListener`。
3. 调整数据源管理接口或租户绑定流程时，检查 `DataSourceController`、`DataSourceManageServiceImpl`、相关 mapper。
4. 调整变更分发和事件模型时，检查 `RemoteDataSourceApi`、`DataSourceChangeDispatcher`、`DataSourceChangeEvent`、`DataSourceChangeListener`。
5. 调整请求期数据源切换与租户隔离时，检查 `TenantDataSourceInterceptor`、`TenantDataSourceManager`、`TenantIsolationResolver`。
