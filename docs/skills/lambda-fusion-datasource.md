---
name: "lambda-fusion-datasource"
description: "面向动态数据源中心（server/client 双模式）、租户数据源绑定、Dubbo 广播、运行期连接池增删改与租户拦截注入等需求的模块分析与改造指南。Invoke when 需求涉及数据源/租户库/运行期切库。"
---

# lambda-fusion-datasource 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造动态数据源管理（新增/更新/启停/删除、连接测试、运行期注册/移除连接池）时。
- 需要实现/改造租户与数据源绑定关系、租户 Schema 初始化/清理编排时。
- 需要在多节点/多服务间同步数据源变更（Dubbo 广播 + 本地事件消费）时。
- 需要确保请求进入时自动切换到正确租户数据源时（Web 拦截器链路）。

## 自动装配与入口

- 自动装配入口：DatasourceAutoConfiguration（lambda-fusion-datasource: com.lambda.fusion.autoconfig.DatasourceAutoConfiguration）
- 核心配置类（server/client 条件装配 + WebMvc 拦截器注册）：DatasourceConfigure（com.lambda.fusion.datasource.DatasourceConfigure）
- 配置属性：`lambda.fusion.datasource.*`：DatasourceProperties（com.lambda.fusion.datasource.DatasourceProperties）
- 常量与状态枚举：DatasourceConstants（com.lambda.fusion.datasource.DatasourceConstants）

## 运行模式（server / client）

- `lambda.fusion.datasource.mode=server`（默认）：
  - 装配 `RemoteDataSourceService`（可通过 Dubbo 暴露）与 `ServerDataSourceInitializer`
  - 入口见：DatasourceConfigure（约 L50-L103）
- `lambda.fusion.datasource.mode=client`：
  - 装配 `ClientDataSourceInitializer`（启动拉取 + 订阅）与 `ClientDataSourceChangeListener`（增量消费）
  - 入口见：DatasourceConfigure（约 L104-L120）

## Web 请求租户切库（拦截器顺序）

- 本模块注册 `TenantDataSourceInterceptor` 到 `/**`，并显式要求执行顺序在 core/authority 的租户上下文拦截之后：
  - 配置点：DatasourceConfigure（约 L122-L128）
- 设计意图：
  - 上游（authority）负责解析 tenantId 并写入上下文
  - datasource 在此基础上根据 tenantId 选择/创建对应 DataSource 并绑定到当前请求线程

## Schema 初始化/清理（扩展点）

- 业务侧可实现：
  - `TenantSchemaInitializer`：用于 `INIT_SCHEMA` 事件（创建表结构/初始化数据）
  - `TenantSchemaCleaner`：用于 `REMOVE_SCHEMA` 事件（清理租户 Schema）
- 这些扩展点会被 client 端的变更消费逻辑调用，用于把“数据源变更”扩展为“可运行的租户库”。

## 数据库与 Liquibase

- Changelog：lambda-datasource-changelog.xml（lambda-fusion-datasource/src/main/resources/META-INF/db/changelogs/）
- 表结构历史/初始化参考（docs/sql）：docs/sql/la_datasources.sql、docs/sql/la_tenant_datasource.sql

## 常见改造任务指引

- **新增数据源用途（usageType）**：同步扩展 core 的 `FusionConstants.DatabaseUsageType`（如存在）与数据字典输出；并在绑定/校验逻辑中加入新用途约束。
- **增强 client 拉取稳定性**：收敛到 DatasourceProperties.Retry（指数退避），避免在失败时阻塞主线程。
- **切库问题排查**：优先确认“tenantId 是否已注入”与“拦截器顺序”是否正确，再看 DynamicDataSourceService 的数据源注册情况。
