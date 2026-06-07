---
name: "lambda-fusion-permission"
description: "权限聚合模块（API 权限元数据同步 + 数据权限授权树）。当需求涉及 API 权限上报/匹配或数据权限分配与智能继承时调用。"
---

# Lambda Fusion Permission

该模块是聚合模块（`packaging=pom`），实际能力由两个子模块提供：
- `lambda-fusion-permission-api`
- `lambda-fusion-permission-datascope`

## 模块引入
按需引入子模块：

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-permission-api</artifactId>
</dependency>

<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-permission-datascope</artifactId>
</dependency>
```

## permission-api

### 自动配置
- 自动配置入口：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/autoconfig/PermissionAutoConfiguration.java`
- 模块装配类：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/PermissionConfigure.java`
- 配置属性：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/PermissionProperties.java`
- AutoConfiguration imports：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 配置
配置前缀是 `lambda.fusion.permission`。当前关键配置包括：
- `enabled`
- `mode`
- `client.check-enabled`
- `client.deny-unmatched`
- `client.push-enabled`
- `client.fail-fast`
- `client.push-interval-seconds`
- `client.resource-path`
- `client.auth-token`
- `server.auth-token`

### 主要能力
- 本地权限 JSON 文件加载
- API 权限元数据注册与本地匹配
- client 模式下权限元数据推送
- server 模式下权限元数据接收与注册
- Dubbo 方式同步权限元数据

### 入口定位
- 配置常量：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/PermissionConstants.java`
- 本地文件加载器：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/loader/LocalPermissionLoader.java`
- 注册表：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/service/ApiPermissionRegistry.java`
- 匹配器：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/service/ApiPermissionMatcher.java`
- 推送客户端：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/client/PermissionPushClient.java`
- client 初始化器：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/client/PermissionClientInitializer.java`
- 同步接口：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/api/PermissionSyncApi.java`
- server 实现：`lambda-fusion-permission/lambda-fusion-permission-api/src/main/java/com/lambda/fusion/permission/server/PermissionSyncApiImpl.java`

### 关键机制
- 默认 `mode=client`
- `LocalPermissionLoader` 从 `classpath*:` 资源路径加载一个或多个权限 JSON 文件
- client 模式会将本地权限注册到 `ApiPermissionRegistry`，并可按定时任务重复推送
- server 模式通过 `PermissionSyncApi` 接收推送并写入注册表
- Dubbo client/server 都是条件装配，依赖类路径和 `mode`

## permission-datascope

### 自动配置
- 自动配置入口：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/autoconfig/DataScopeAutoConfiguration.java`
- 模块装配类：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/DataScopeConfigure.java`
- 配置属性：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/DataScopeProperties.java`
- AutoConfiguration imports：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Liquibase 变更脚本：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/resources/META-INF/db/changelogs/lambda-datascope-changelog.xml`

### 配置
配置前缀是 `lambda.fusion.datascope`。当前关键配置包括：
- `smart.enabled`
- `smart.types.*`

### 主要能力
- 数据权限授权树查询
- 数据权限授权保存
- 基于 `DataViewProvider` 的业务树加载
- smart datascope 事件驱动继承与联动更新

### 入口定位
- REST 控制器：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/controller/DataScopeController.java`
- 授权服务：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/service/impl/DataScopeGrantServiceImpl.java`
- 智能授权服务：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/service/impl/DataScopeSmartServiceImpl.java`
- Provider 接口：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/provider/DataViewProvider.java`
- 事件模型：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/event/DataScopeObjectChangedEvent.java`
- smart 监听器：`lambda-fusion-permission/lambda-fusion-permission-datascope/src/main/java/com/lambda/fusion/datascope/listener/DataScopeSmartEventListener.java`

### 关键机制
- `DataScopeController` 只暴露授权树查询和授权保存两个核心接口
- 业务树本身由 `DataViewProvider` 提供，模块不内置具体业务数据实现
- `smart.types` 按 businessKey 配置受影响的数据权限类型
- `DataScopeSmartEventListener` 监听业务对象变更事件，触发 CREATED/UPDATED/DELETED/MOVED 的权限继承调整

## 条件与依赖说明
- `permission-api` 的 Dubbo client/server 都依赖 Dubbo 类路径条件
- `permission-api` 中 server/client Bean 受 `lambda.fusion.permission.mode` 控制
- `permission-api` 的推送行为还受 `client.push-enabled` 控制
- `datascope` 的 smart 监听器总是装配，但只有 `smart.enabled=true` 且命中 `smart.types` 时才真正执行联动逻辑

## 常见改造入口
1. 调整权限 JSON 文件格式、加载路径或推送策略时，优先检查 `PermissionProperties`、`LocalPermissionLoader`、`PermissionClientInitializer`、`PermissionPushClient`。
2. 调整 API 权限注册、匹配或服务端同步逻辑时，检查 `ApiPermissionRegistry`、`ApiPermissionMatcher`、`PermissionSyncApiImpl`。
3. 调整数据权限授权树结构时，检查 `DataScopeGrantServiceImpl`、`DataViewProvider`、`DataScopeMapper`。
4. 调整 smart datascope 的继承、移动、删除联动逻辑时，检查 `DataScopeProperties`、`DataScopeSmartEventListener`、`DataScopeSmartServiceImpl`。
