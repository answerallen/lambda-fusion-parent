---
name: "lambda-fusion-authority-api"
description: "面向外部服务接入统一认证（Sa-Token StpInterface）与 Dubbo 远程引用 RemoteAuthenticationService 的客户端侧模块指南。Invoke when 需要在业务服务侧消费认证中心能力。"
---

# lambda-fusion-authority-api 模块 Skill

## 适用范围（何时使用）

- 业务服务需要“通过远程认证中心”获取用户权限/角色/菜单等鉴权数据，并将其挂接到 Sa-Token（StpInterface）时。
- 需要以 Dubbo 引用方式接入 `RemoteAuthenticationService`，而不是在本服务内实现 StpInterface 时。

## 模块定位与边界

- 本模块是 **authority 的客户端依赖**：只定义客户端要引用的接口，以及自动装配把它桥接成 Sa-Token 的 `StpInterface`。
- 服务端实现位于 lambda-fusion-authority 模块。

## 关键入口

- 远程认证接口（Sa-Token StpInterface 透传）：RemoteAuthenticationService（lambda-fusion-authority-api: com.lambda.fusion.authority.api.RemoteAuthenticationService）
- 自动装配（Dubbo Reference + StpInterface 注入）：AuthorityClientAutoConfiguration（lambda-fusion-authority-api: com.lambda.fusion.autoconfig.AuthorityClientAutoConfiguration）
  - Dubbo 存在时创建 `ReferenceBean<RemoteAuthenticationService>`（缺省 Bean 才创建）
  - 统一把 `RemoteAuthenticationService` 暴露为 `StpInterface`（Sa-Token 鉴权入口）

## 常见接入/改造任务指引

- **业务服务要启用远程鉴权**：
  - 引入本模块依赖；
  - 确保 Dubbo 客户端已启用并能发现 authority 服务端的 `RemoteAuthenticationService`；
  - 确认本服务没有自定义 `StpInterface` Bean（否则不会注入远程实现）。
- **要切换为非 Dubbo 方式**：
  - 需要在业务服务侧自行提供 `RemoteAuthenticationService` 或直接提供 `StpInterface` Bean；
  - 同时关注 Sa-Token 的鉴权缓存策略与网络容错。
