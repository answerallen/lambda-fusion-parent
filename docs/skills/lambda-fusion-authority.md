---
name: "lambda-fusion-authority"
description: "面向用户/角色/资源/组织/租户/客户端/令牌、登录态与权限上下文、租户拦截、Dubbo 远程认证暴露等需求的模块分析与改造指南。Invoke when 需求涉及认证授权域。"
---

# lambda-fusion-authority 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造登录与鉴权、用户/角色/资源/组织/租户/客户端/令牌管理、菜单与权限聚合输出时。
- 需要处理多租户上下文注入、基于域名/请求头解析租户、租户初始化编排（与 datasource/config 模块联动）时。
- 需要对外暴露/接入 Dubbo 远程认证（Sa-Token StpInterface）时（服务端在本模块，客户端在 authority-api 模块）。

## 模块定位与边界

- 认证与权限域的“业务中心”：提供 RBAC 领域模型与管理 API，统一输出登录上下文（用户信息、菜单、权限等）。
- 对外依赖：
  - 功能权限 / 数据权限来自 lambda-fusion-permission
  - 可选：数据源中心与配置中心（lambda-fusion-datasource、lambda-fusion-config）

## 自动装配与入口

- 自动装配入口：AuthorityAutoConfiguration（lambda-fusion-authority: com.lambda.fusion.autoconfig.AuthorityAutoConfiguration）
  - `before = SecurityAutoConfiguration`，确保在安全体系之前注入本模块行为。
- 模块配置类（扫描/拦截器/线程池/Dubbo 服务/监听器等）：AuthorityConfigure（com.lambda.fusion.authority.AuthorityConfigure）
- 配置属性：`lambda.fusion.authorize.*`：AuthorityProperties（com.lambda.fusion.authority.AuthorityProperties）
- 常量与字典枚举（角色类型、菜单类型、默认角色等）：AuthorityConstants（com.lambda.fusion.authority.AuthorityConstants）

## 关键机制（按问题快速定位）

### 1) 租户上下文注入（Web 拦截器）

- `TenantContextInterceptor` 由 AuthorityConfigure 注册到 `/**`，并设置高优先级执行：
  - 注册点：AuthorityConfigure（约 L161-L166）
  - 拦截器实现：TenantContextInterceptor（com.lambda.fusion.authority.commons.interceptor.TenantContextInterceptor）
- 与动态数据源模块的执行顺序：
  - datasource 模块的 `TenantDataSourceInterceptor` 注释明确要求其在此拦截器之后执行（见 datasource 的 WebMvcConfigurer 配置）。

### 2) MyBatis 元数据填充（创建人/更新人等）

- 通过 `EntityMetaFiller` 在插入/更新时注入 createdAt/createdBy/updatedAt/updatedBy：
  - 入口：AuthorityConfigure（约 L77-L100）
  - 注意 AuthUtils 在匿名上下文下的行为（可能返回空/抛异常），变更时要保证兼容。

### 3) 远程认证服务（Dubbo + Sa-Token）

- 服务端：AuthorityConfigure 在 Dubbo 存在时通过 `@DubboService` 暴露 `RemoteAuthenticationService`：
  - 入口：AuthorityConfigure（约 L64-L75）
  - 适配器：RemoteAuthenticationServiceAdapter（com.lambda.fusion.authority.commons.adapter.RemoteAuthenticationServiceAdapter）
- 客户端：见 authority-api 模块 skill 文档。

### 4) 在线状态与事件监听

- Sa-Token 事件监听（可选）：UserOnlineLogListener（com.lambda.fusion.authority.commons.listener.UserOnlineLogListener）
- SSE 事件监听（可选）：UserSeeEventListener（com.lambda.fusion.authority.commons.listener.UserSeeEventListener）

## Controller 与对外 API（入口定位）

- Controller 包目录：com.lambda.fusion.authority.controller
  - AuthenticationController / UserController / RoleController / ResourceController
  - OrganizationController / TenantController / ClientController / AreaController

## 数据库与 Liquibase

- Changelog：
  - lambda-authority-changelog.xml（lambda-fusion-authority/src/main/resources/META-INF/db/changelogs/）
  - lambda-authority-data-init-changelog.xml（lambda-fusion-authority/src/main/resources/META-INF/db/changelogs/）
- SQL 模板与历史表结构参考：docs/sql/

## 常见改造任务指引

- **扩展用户信息输出/菜单路由**：优先从 AuthenticationServiceImpl 入手，检查权限聚合与菜单树构建逻辑，再调整对应 Controller 返回模型。
- **新增资源类型或权限模型字段**：同步修改 model + mapper xml + changelog，并确认前端/权限中心（permission 模块）是否依赖该字段。
- **调整租户解析策略**：修改 TenantContextInterceptor，但保持“请求结束清理上下文”的语义不变，避免线程复用导致串租户。
