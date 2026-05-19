---
name: "lambda-fusion-permission"
description: "面向功能权限（API 鉴权与权限点上报）与数据权限（DataScope 控制面与 Smart 同步配置）两类需求的模块分析与改造指南。Invoke when 需求涉及接口鉴权/权限元数据/数据范围授权。"
---

# lambda-fusion-permission 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造 API 级别鉴权（RBAC、接口权限点、请求拦截、未匹配策略）时。
- 需要实现/改造权限元数据在多服务之间的上报与汇聚（Dubbo RPC）时。
- 需要实现/改造数据权限控制面（授权树、授权记录维护、Smart 自动同步触发配置）时。

## 模块结构与边界

- 本聚合模块包含两个子模块：
  - `lambda-fusion-permission-api`：功能权限（API 权限点定义/加载/上报/匹配）
  - `lambda-fusion-permission-datascope`：数据权限控制面（授权树与 Smart 同步配置）
- 数据权限的数据面（SQL 改写/拦截）不在本仓库该模块中，通常由 `lambda-cloud-starter-mybatis` 协同实现。

## 功能权限（permission-api）

### 关键入口

- 自动装配入口：PermissionAutoConfiguration（lambda-fusion-permission-api: com.lambda.fusion.autoconfig.PermissionAutoConfiguration）
- 配置类（client/server 条件装配 + Dubbo 引用/暴露）：PermissionConfigure（com.lambda.fusion.permission.PermissionConfigure）
- 配置属性：`lambda.fusion.permission.*`：PermissionProperties（com.lambda.fusion.permission.PermissionProperties）
- 常量：PermissionConstants（com.lambda.fusion.permission.PermissionConstants）

### client / server 模式

- `lambda.fusion.permission.mode=client`（默认）：
  - 读取本地权限声明（LocalPermissionLoader）
  - 可选推送到中心（PermissionPushClient）
  - 可选 Dubbo 引用中心的 PermissionSyncApi（缺省 Bean 才创建）
- `lambda.fusion.permission.mode=server`：
  - 暴露 PermissionSyncApi 的实现（PermissionSyncApiImpl）
  - 维护全局 ApiPermissionRegistry，承接多服务权限点汇聚

### 修改建议（常见改造）

- **新增权限点声明格式**：优先改 LocalPermissionLoader 的解析逻辑，并保证 ApiPermissionMetadata 的字段兼容。
- **调整未匹配策略**：以 `client.denyUnmatched` 为收口，避免在拦截器里硬编码。
- **变更上报安全性**：client/server 的 `authToken` 需要一致，禁止日志输出敏感 token。

## 数据权限（permission-datascope）

### 关键入口

- 自动装配入口：DataScopeAutoConfiguration（lambda-fusion-permission-datascope: com.lambda.fusion.autoconfig.DataScopeAutoConfiguration）
- 配置类：DataScopeConfigure（com.lambda.fusion.datascope.DataScopeConfigure）
- 配置属性：`lambda.fusion.datascope.*`：DataScopeProperties（com.lambda.fusion.datascope.DataScopeProperties）
- 常量：DataScopeConstants（com.lambda.fusion.datascope.DataScopeConstants）

### Smart 同步（配置驱动）

- Smart 同步通过 `DataScopeProperties.smart.*` 控制：
  - enabled：是否开启
  - types：按业务类名映射到 domainType 数组（用于决定哪些变更事件触发哪些域的同步）
  - 读取入口：DataScopeProperties（约 L21-L30）

## 数据库与 Liquibase

- datascope changelog：lambda-datascope-changelog.xml（lambda-fusion-permission-datascope/src/main/resources/META-INF/db/changelogs/）
- 表结构历史/初始化参考（docs/sql）：docs/sql/la_api_resources.sql
