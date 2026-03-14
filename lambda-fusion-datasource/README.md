# Lambda Fusion Datasource

`lambda-fusion-datasource` 是 Lambda Fusion 的动态数据源模块，提供：
- **全局数据源管理**（增删改查、启停、连接测试）
- **租户数据源绑定**（支持按用途绑定：租户主库 / AI 专属库）
- **服务端/客户端双模式同步**（基于 Dubbo 与本地事件的变更广播）
- **运行时动态数据源接入**（无缝集成 `DynamicDataSourceService`）

---

## 1. 模块定位与能力边界

### 1.1 模块定位
- 这是一个**数据源控制面 + 运行时同步层**模块，不负责具体的业务表增删改查。
- 核心目标是统一管理数据源元信息，并将数据源变更（如新增、禁用）实时同步到分布式系统的各个节点的本地连接池中。
- 在多租户场景下，提供“租户与特定用途数据源”的绑定与初始化编排能力。

### 1.2 能力边界
- **负责**：数据源元数据管理、多用途租户绑定关系管理、动态数据源的注册与移除、基于 Dubbo 的配置变更广播。
- **不负责**：业务 SQL 执行、业务领域模型管理、具体的业务数据迁移。
- **扩展点**：Schema 初始化与清理逻辑由业务侧通过实现 `TenantSchemaInitializer` / `TenantSchemaCleaner` 插件化扩展。

### server 模式（默认）
- 启动时 `ServerDataSourceInitializer` 从本地表 `la_datasources` 分页加载在线数据源。
- 注册 Dubbo 服务 `RemoteDataSourceService`。
- 数据源变更通过 `DataSourceListener` -> `DataSourceChangeDispatcher` 广播给客户端。

### client 模式
- `ClientDataSourceInitializer` 异步初始化，使用指数退避重试拉取远程数据源。
- 首次拉取 `listEnabled()` 并注册到本地 `DynamicDataSourceService`。
- 通过 `subscribe(clientId, callback)` 订阅增量事件。
- 支持 `INIT_SCHEMA` / `REMOVE_SCHEMA` 事件，回调 `TenantSchemaInitializer` / `TenantSchemaCleaner`。

---

## 2. 核心运行模式

配置前缀：`lambda.fusion.datasource`

### 2.1 server 模式（默认）
- 启动时 `ServerDataSourceInitializer` 从本地表 `la_datasources` 分页加载已启用的在线数据源。
- 注册并暴露 Dubbo RPC 服务 `RemoteDataSourceService`。
- 本地数据源的增删改停操作，会通过 `DataSourceListener` -> `DataSourceChangeDispatcher` 广播给所有订阅的客户端节点。

### 2.2 client 模式
- 启动时 `ClientDataSourceInitializer` 在后台线程异步初始化，使用指数退避重试机制拉取远程数据源（容忍远端暂不可用）。
- 首次成功拉取 `listEnabled()` 后，将其注册到本地的 `DynamicDataSourceService` 连接池中。
- 通过 `subscribe(clientId, callback)` 注册回调，持续监听远端配置增量变更。
- 支持接收 `INIT_SCHEMA` / `REMOVE_SCHEMA` 广播事件，通过触发本地 `TenantSchemaInitializer` / `TenantSchemaCleaner` 完成特定租户库的 DDL 初始化或清理操作。

---

## 3. 模块装配机制

- 自动装配入口：`DatasourceAutoConfiguration`，由 `AutoConfiguration.imports` 注册。
- 统一配置类：`DatasourceConfigure`，按 `lambda.fusion.datasource.mode` 条件装配。
- server 模式创建：
  - `RemoteDataSourceService`（Dubbo 发布服务）
  - `ServerDataSourceInitializer`（本地数据库 -> 动态数据源加载）
- client 模式创建：
  - `ClientDataSourceInitializer`（远程拉取 + 订阅）
  - `ClientDataSourceChangeListener`（消费增量变更）

---

## 4. 数据模型与关键约束

### 4.1 全局数据源 `la_datasources`
对应实体：`DataSourceEntity`

关键字段：
- `datasource_key`：逻辑唯一标识（业务可读标识，如 `tenant_main_001`）
- `usage_type`：`FusionConstants.DatabaseUsageType`（必须来源于 `DATABASE_USAGE_TYPE` 字典值，例如 1=AI, 2=TENANT）
- `status`：`DatasourceConstants.DatasourceStatus`（`DATASOURCE_STATUS` 字典值，1=在线，0=下线）
- `jdbc_url/username/password`：标准 JDBC 连接信息

### 4.2 租户绑定表 `la_tenant_datasource`
对应实体：`TenantDataSourceEntity`

关键字段：
- `tenant_id`：目标租户 ID
- `datasource_key`：关联的数据源标识
- `usage_type`：用途约束（一个租户下，每种用途只能绑定一个数据源，例如可同时绑定一个 TENANT 主库和一个 AI 专属库）
- `schema_status`：标识库结构初始化状态（0=未初始化 / 1=已初始化），仅在针对租户主库的初始化链路中起效。

---

## 5. REST API (`DataSourceController`)

> Base Path: `/datasource`

### 5.1 全局数据源
用于系统管理员维护底层物理数据库连接信息：
- `GET /page`：分页查询
- `GET /list`：全量列表查询
- `GET /{id}`：获取详情
- `POST /`：新增数据源记录
- `PUT /{id}`：更新数据源记录
- `DELETE /{id}`：删除数据源（关联删除运行态连接池）
- `GET /{id}/test`：连接可用性测试
- `PUT /{id}/enable`：启用（状态设为 1，并加载到运行态连接池）
- `PUT /{id}/disable`：禁用（状态设为 0，并从运行态连接池移除）

### 5.2 租户数据源绑定
用于为多租户系统配置和映射专属数据库：
- `GET /tenant/status?tenantIds=...`：批量查询指定租户们的绑定状态与初始化状态。
- `GET /tenant/{tenantId}`：查询指定租户名下，当前绑定的所有数据源清单（例如返回一条 TENANT 用途和一条 AI 用途的绑定关系）。
- `PUT /tenant/{tenantId}/bind?datasourceKey=...&usageType=...`：按指定用途绑定数据源。
  - **重要约束：** 请求的 `usageType` 必须与目标 `DataSourceEntity` 自身的 `usage_type` 严格一致，否则会触发绑定失败。绑定 TENANT 用途会重置 `schema_status = 0`。
- `POST /tenant/{tenantId}/init`：初始化租户主库。
  - 仅读取该租户下 `usageType=TENANT` 的记录，调用内部 `initSchema` 逻辑广播执行 DDL，成功后置 `schema_status = 1`。

> *提示：API 签名中的 `usageType` 入参统一使用整型（字典值），在服务层内部转化为 `FusionConstants.DatabaseUsageType` 进行校验与处理。*

---

## 6. Dubbo RPC (`RemoteDataSourceService`)

能力包括：
- 全量/启用查询：`listAll/listEnabled/get`
- 变更操作：`add/update/delete/enable/disable/test`
- 回调订阅：`subscribe/unsubscribe`
- schema 事件：`initSchema/removeSchema`

server 端实现：`RemoteDataSourceServiceImpl`  
client 端回调处理：`ClientDataSourceChangeListener`

---

## 7. 关键业务流程

### 7.1 端到端运行原理（总览）

```text
管理端配置 / API 调用
         ↓
DataSourceManageService (持久化 la_datasources/la_tenant_datasource 元数据)
         ↓
同步至当前节点的 DynamicDataSourceService（增删改连接池实例）
         ↓
发布 DataSourceEvent（Spring 本地事务事件）
         ↓
DataSourceListener (AFTER_COMMIT 阶段) -> 封装为 DataSourceChangeEvent -> 广播 Dubbo 事件
         ↓
其它节点 ClientDataSourceChangeListener 接收事件 -> 同步至其本地 DynamicDataSourceService
```

### 7.2 租户用途绑定（TENANT / AI）
1. `bindTenantDataSource(tenantId, datasourceKey, usageType)` 校验该租户为**独立库模式** (`DEDICATED`)。
2. 校验目标数据源存在，且 `DataSourceEntity.usageType` 与请求中的 `usageType` 严格一致。
3. 在 `la_tenant_datasource` 中按 `(tenantId, usageType)` 维度写入/更新绑定记录。
4. 若为 `TENANT` 绑定，则将 `schema_status` 重置为 `0`（未初始化）。

### 7.3 租户主库 Schema 初始化
1. `POST /tenant/{tenantId}/init`。
2. 读取 `usageType=TENANT` 的租户绑定记录。
3. 调用 `RemoteDataSourceService.initSchema(id)` 广播 schema 初始化事件。
4. 客户端监听到 `INIT_SCHEMA` 事件，执行已注册的 `TenantSchemaInitializer` 完成建表等操作。
5. 操作成功后，服务端将 `schema_status` 标记为 `1`。

---

## 8. 租户隔离与缓存

`TenantIsolationResolver` 通过 `LA_TENANT.isolation_mode` 判断租户的隔离模式：
- `SHARED`：走共享库逻辑，忽略独立绑定。
- `DEDICATED`：独立库模式，允许并需要绑定独立数据源。

为了降低数据库查询压力，内部使用了基于 `ConcurrentHashMap` 的 30 秒本地短缓存。

---

## 9. 编程式数据源切换 (`DataSourceSwitcher`)

模块提供了开箱即用的上下文切换工具，底层封装了 `baomidou` 的 `DynamicDataSourceContextHolder`，并支持 `try-with-resources` 自动恢复。

```java
@Autowired
private TenantDataSourceManager tenantDataSourceManager;

public void doSomethingInTenantDb(String tenantId) {
    // 1. 获取目标租户特定用途的数据源名称
    String dsName = tenantDataSourceManager.getTenantDataSourceName(tenantId, "tenant_");

    // 2. 编程式切换数据源，退出 try 块时自动恢复原数据源
    try (DataSourceSwitcher switcher = DataSourceSwitcher.switchTo(dsName)) {
        // 在这里执行的 mybatis mapper 调用将路由到该租户的独立数据库
        userMapper.selectList(null);
    }
}
```

---

## 10. 接入说明与最佳实践

### 10.1 引入依赖

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-datasource</artifactId>
    <version>${revision}</version>
</dependency>
```

### 10.2 服务端模式（推荐用于数据源管理中心）

```yaml
lambda:
  fusion:
    datasource:
      mode: server
      dubbo:
        group: datasource
        version: 1.0.0
```

使用步骤：
1. 启动应用后，模块自动从 `la_datasources` 加载在线数据源。
2. 业务运营人员通过 `/datasource` 系列 API（或前端页面）维护数据源。
3. 对数据源和绑定的增删改会触发 Dubbo 事件广播。

### 10.3 客户端模式（推荐用于各业务微服务）

```yaml
lambda:
  fusion:
    datasource:
      mode: client
      retry:
        max-attempts: 5
        initial-delay: 5000
        multiplier: 2.0
        max-delay: 60000
```

使用步骤：
1. 应用启动后后台线程异步拉取远端在线数据源（若远端不可用，将按指数退避策略重试）。
2. 拉取成功后，自动将其注入本地连接池，并开始监听远端下发的配置变更。
3. 微服务只需关心如何按租户 ID 执行 SQL 即可。

### 10.4 常用管理端接口调用示例

新增全局数据源：

```bash
curl -X POST 'http://localhost:8080/datasource' \
  -H 'Content-Type: application/json' \
  -d '{
    "datasourceKey": "tenant_main_001",
    "datasourceName": "租户主库-001",
    "dbType": "mysql",
    "usageType": 2,
    "jdbcUrl": "jdbc:mysql://127.0.0.1:3306/tenant_main_001",
    "username": "root",
    "password": "******",
    "nodeRole": "PRIMARY",
    "status": 1
  }'
```

绑定租户主库（TENANT）：

```bash
curl -X PUT 'http://localhost:8080/datasource/tenant/t1001/bind?datasourceKey=tenant_main_001&usageType=2'
```

绑定租户 AI 库（AI）：

```bash
curl -X PUT 'http://localhost:8080/datasource/tenant/t1001/bind?datasourceKey=tenant_ai_001&usageType=1'
```

初始化租户主库：

```bash
curl -X POST 'http://localhost:8080/datasource/tenant/t1001/init'
```

### 10.5 最佳实践建议

- **统一管控**：在生产环境，强烈建议统一由 `server` 模式的管理中心负责维护和分发数据源元信息，其它所有业务微服务节点配置为 `client` 模式被动订阅。
- **用途约束**：发起 `bind` 操作时，入参 `usageType` 必须与目标数据源注册时的 `usageType` 一致，否则绑定请求将被拒绝。
- **初始化时机**：TENANT 绑定会重置初始化状态为未初始化（`0`），因此在租户绑定新的主库后，通常应紧接着调用一次 `/init` 接口完成表结构重建。

---

## 11. 配置全览

```yaml
lambda:
  fusion:
    datasource:
      mode: server # 可选: server (默认) | client
      default-tenant-prefix: tenant_ # 租户数据源在连接池中的命名前缀
      dubbo:
        group: datasource # Dubbo 广播组
        version: 1.0.0
      retry: # 客户端拉取配置重试策略
        max-attempts: 5
        initial-delay: 5000
        multiplier: 2.0
        max-delay: 60000
```

---

## 12. 版本演进记录 (Changelog)

- **[2026.1] 租户多用途绑定支持**：
  - 取消了原 `TenantDataSourceController` / `TenantDataSourceManageService`。
  - 租户绑定能力收敛并升级，支持按 `usageType` (TENANT / AI) 为一个租户绑定多个专属数据库。
  - `la_tenant_datasource` 表新增 `USAGE_TYPE` 字段，存量历史数据通过 liquibase 默认刷为 `2` (TENANT)。
  - `client` 模式增加了异步重试与容错降级能力。
