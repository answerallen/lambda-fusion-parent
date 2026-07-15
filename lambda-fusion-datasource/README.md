# Lambda Fusion Datasource

`lambda-fusion-datasource` 是 Lambda Fusion 的动态数据源模块，提供：
- **全局数据源管理**（增删改查、启停、连接测试）
- **服务端/客户端双模式同步**（基于 Dubbo 与本地事件的变更广播）
- **运行时动态数据源接入**（集成 `DynamicDataSourceService`）

---

## 1. 模块定位与能力边界

- **数据源控制面 + 运行时同步层**，不负责具体业务表的增删改查。
- 统一管理数据源元信息，将变更实时同步到各节点本地连接池。

| 维度 | 说明 |
|------|------|
| **负责** | 数据源元数据管理、动态数据源注册与移除、Dubbo 变更广播 |
| **不负责** | 业务 SQL 执行、领域模型管理、业务数据迁移 |

---

## 2. 核心运行模式

配置前缀：`lambda.fusion.datasource`

### 2.1 server 模式（默认）
- 启动时 `ServerDataSourceInitializer` 从 `la_datasources` 分页加载已启用数据源。
- 注册 Dubbo 服务 `RemoteDataSourceService`。
- 数据源增删改停操作通过 `DataSourceListener` -> `DataSourceChangeDispatcher` 广播给客户端。

### 2.2 client 模式
- `ClientDataSourceInitializer` 后台异步初始化，指数退避重试拉取远程数据源。
- 首次拉取 `listEnabled()` 后注册到本地 `DynamicDataSourceService`。
- 通过 `subscribe(clientId, callback)` 监听增量变更。

---

## 3. 模块装配机制

- 自动装配入口：`DatasourceAutoConfiguration`，由 `AutoConfiguration.imports` 注册。
- 配置类 `DatasourceConfigure` 按 `lambda.fusion.datasource.mode` 条件装配。
- **server** 模式装配：`RemoteDataSourceService`（Dubbo 服务）、`ServerDataSourceInitializer`
- **client** 模式装配：`ClientDataSourceInitializer`（远程拉取 + 订阅）、`ClientDataSourceChangeListener`（增量消费）

---

## 4. 数据模型

### 4.1 全局数据源 `la_datasources`
实体：`DataSourceEntity`

| 字段 | 说明 |
|------|------|
| `id` | 数据源唯一标识 |
| `usage_type` | `FusionConstants.DatabaseUsageType`，字典 `DATABASE_USAGE_TYPE`（1=AI, 3=BUSINESS） |
| `status` | `DatasourceConstants.DatasourceStatus`，字典 `DATASOURCE_STATUS`（1=在线, 0=下线） |
| `jdbc_url/username/password` | 标准 JDBC 连接信息 |

---

## 6. 关键业务流程

### 6.1 端到端运行原理

```text
管理端 API 调用
     ↓
DataSourceManageService（持久化元数据）
     ↓
DynamicDataSourceService（增删改连接池）
     ↓
DataSourceEvent（Spring 本地事务事件）
     ↓
DataSourceListener (AFTER_COMMIT) -> DataSourceChangeEvent -> Dubbo 广播
     ↓
ClientDataSourceChangeListener -> 同步至本地 DynamicDataSourceService
```

---

---

## 9. 编程式数据源切换 (`DataSourceSwitcher`)

底层封装 `baomidou` 的 `DynamicDataSourceContextHolder`，支持 `try-with-resources` 自动恢复。用于多数据源/多库类型等通用场景（与租户隔离解耦）。

```java
try (DataSourceSwitcher switcher = DataSourceSwitcher.switchTo("ai-postgres")) {
    // 在指定数据源上执行
    userMapper.selectList(null);
}
```

---

## 10. 接入说明

### 10.1 引入依赖

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-datasource</artifactId>
    <version>${revision}</version>
</dependency>
```

### 10.2 server 模式

```yaml
lambda:
  fusion:
    datasource:
      mode: server
      dubbo:
        group: datasource
        version: 1.0.0
```

启动后自动从 `la_datasources` 加载在线数据源，通过 `/datasource` API 维护，变更触发 Dubbo 广播。

### 10.3 client 模式

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

后台异步拉取远端数据源（不可用时指数退避重试），成功后注入本地连接池并监听变更。

### 10.4 注意事项

- **统一管控**：生产环境建议由 `server` 模式管理中心维护数据源，业务服务配为 `client` 被动订阅。

---

## 11. 配置全览

```yaml
lambda:
  fusion:
    datasource:
      mode: server                    # server (默认) | client
      dubbo:
        group: datasource
        version: 1.0.0
      retry:                          # client 模式重试策略
        max-attempts: 5
        initial-delay: 5000
        multiplier: 2.0
        max-delay: 60000
```

---

