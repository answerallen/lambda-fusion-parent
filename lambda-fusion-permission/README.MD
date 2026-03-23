# Lambda Fusion Permission

`lambda-fusion-permission` 是 Lambda Fusion 框架的核心权限管理组件，旨在为微服务及单体应用提供**全方位、多维度的安全管控**。

该项目将权限控制严格划分为**功能权限（API 访问控制）**与**数据权限（行级数据隔离）**两大独立子模块，实现了“能看什么页面/调什么接口”与“能看哪些数据”的关注点分离。

## 模块结构

本项目包含以下两个核心子模块：

### 1. 功能权限模块 ([lambda-fusion-permission-api](./lambda-fusion-permission-api/README.MD))
负责**基于角色的 API 访问控制 (RBAC)**。
- **核心能力**：
  - 静态接口权限定义与解析（JSON 声明式权限）。
  - 微服务启动时的权限元数据自动收集与集中上报（基于 Dubbo RPC）。
  - HTTP 请求的实时拦截与鉴权（基于 Sa-Token）。
- **解决的问题**：用户/角色是否有权调用 `/api/v1/users` 接口？

### 2. 数据权限模块 ([lambda-fusion-permission-datascope](./lambda-fusion-permission-datascope/README.MD))
负责**行级数据安全隔离与动态过滤 (RLS)**，作为数据权限的**控制面**。
- **核心能力**：
  - 提供统一的数据授权接口（支持按用户、角色、机构等多种维度授权）。
  - 基于策略模式 (`DataViewProvider`) 的权限树构建，无需物理数据库视图。
  - Smart 事件驱动同步机制，随业务数据（如部门变动）自动维护权限关系，杜绝权限孤儿。
  - 与数据面 `lambda-cloud-starter-mybatis` 协同，基于 AST 语法树在 MyBatis 拦截器层动态改写 SQL。
- **解决的问题**：用户调用 `/api/v1/users` 接口时，只能查询到**本部门及下属部门**的用户数据。

## 架构协同与设计哲学

在 Lambda Fusion 中，功能权限与数据权限是**正交**的：
1. **API 拦截优先**：当请求到达时，`PermissionSecureInterceptor` 首先校验用户是否拥有该接口的调用权限。如果无权，直接拦截返回 403。
2. **数据过滤延后**：如果 API 鉴权通过，请求进入 Service/Dao 层执行数据库查询时，`DataScopeInterceptor` (位于 MyBatis Starter 中) 会在最底层根据当前登录用户的身份（及其拥有的 DataScope 权限记录）自动改写 SQL，追加 `WHERE (target_type='USER' AND tid='xxx')` 等条件。

这种设计使得业务代码可以保持纯粹的逻辑，实现**权限代码零侵入**：
```java
// 业务代码中无需任何权限判断
@GetMapping("/list")
public List<User> getUserList() {
    // 1. 进入前已经过 API 权限校验
    // 2. mapper.selectAll() 执行时，SQL 已被底层拦截器自动注入了数据过滤条件
    return userMapper.selectAll();
}
```

## 快速导航
- 深入了解接口鉴权与上报机制，请查看 [API 权限模块文档](./lambda-fusion-permission-api/README.MD)。
- 深入了解数据授权策略、Smart 自动同步与 SQL 改写原理，请查看 [数据权限模块文档](./lambda-fusion-permission-datascope/README.MD)。
