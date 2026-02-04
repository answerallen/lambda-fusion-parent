# Lambda Fusion Datasource

Lambda Fusion Datasource 是 Lambda Fusion 框架中的动态数据源管理模块，基于 Lambda Cloud 构建，提供企业级的多数据库连接管理能力，支持全局和租户特定数据源、实时同步、分布式系统间的数据源变更通知。

## 项目概述

本项目是 Lambda Fusion 框架的核心数据源管理模块，专为企业级微服务应用设计，提供动态数据源管理、多租户数据隔离、分布式同步等功能。作为 Lambda Cloud 生态的一部分，支持服务端和客户端双模式运行，实现数据源的统一管理和实时同步。

### 核心特性

- **动态数据源管理**：运行时添加、更新、删除、启用/禁用数据源
- **多租户支持**：全局数据源和租户特定数据源的分离管理
- **实时同步**：分布式系统间数据源变更的实时传播
- **连接测试**：激活前验证数据库连接性
- **双模式运行**：服务端模式（本地管理）和客户端模式（远程订阅）
- **事件驱动架构**：基于 Spring 事件和 Dubbo 回调的变更通知
- **权限控制**：基于租户上下文的访问控制
- **多数据库支持**：MySQL、PostgreSQL 等 JDBC 兼容数据库

## 技术栈

### 核心技术
- **Java 21+**
- **Spring Boot 3.x**
- **MyBatis Plus** - ORM 框架
- **Dubbo** - RPC 框架
- **Lambda Cloud** - 基础框架依赖

### 数据存储
- **MySQL/PostgreSQL** - 关系型数据库
- **动态数据源** - 运行时数据源管理

### Lambda Cloud Starter 集成
- **lambda-fusion-core** - 框架核心库
- **lambda-cloud-starter-security** - 安全框架
- **lambda-cloud-starter-logger** - 日志框架
- **lambda-cloud-starter-datasource** - 动态数据源支持
- **lambda-cloud-starter-mybatis** - MyBatis 集成
- **lambda-cloud-starter-dubbo** - Dubbo RPC 框架
- **lambda-cloud-starter-cache** - 缓存支持

## 项目结构

```
lambda-fusion-datasource/
├── src/main/java/com/lambda/fusion/
│   ├── autoconfig/
│   │   ├── DatasourceAutoConfiguration.java  # Spring Boot 自动配置
│   │   └── DatasourceProperties.java         # 配置属性绑定
│   └── datasource/
│       ├── api/                              # 远程服务接口和实现
│       │   ├── RemoteDataSourceService.java  # Dubbo 服务接口
│       │   ├── RemoteDataSourceServiceImpl.java # 服务端实现
│       │   ├── DataSourceChangeEvent.java    # 变更事件模型
│       │   └── DataSourceChangeListener.java # 变更监听器接口
│       ├── client/                           # 客户端模式组件
│       │   ├── ClientDataSourceInitializer.java # 客户端初始化器
│       │   └── DataSourceChangeListenerImpl.java # 变更监听器实现
│       ├── controller/                       # REST API 控制器
│       │   ├── DataSourceController.java     # 全局数据源管理 API
│       │   └── TenantDataSourceController.java # 租户数据源管理 API
│       ├── dispatcher/
│       │   └── DataSourceChangeDispatcher.java # 变更事件分发器
│       ├── event/
│       │   ├── DataSourceEvent.java          # Spring 事件模型
│       │   └── DataSourceEventListener.java  # 事件监听器
│       ├── mapper/                           # MyBatis 数据访问层
│       │   ├── DataSourceMapper.java         # 全局数据源映射器
│       │   └── TenantDataSourceMapper.java   # 租户数据源映射器
│       ├── model/                            # 数据模型
│       │   ├── DataSourceEntity.java         # 全局数据源实体
│       │   ├── TenantDataSourceEntity.java   # 租户数据源实体
│       │   ├── RemoteDataSource.java         # 远程传输 DTO
│       │   ├── UpsertDataSource.java         # 创建/更新 DTO
│       │   ├── UpsertTenantDataSource.java   # 租户数据源 DTO
│       │   └── QueryDataSource.java          # 查询 DTO
│       ├── server/                           # 服务端模式组件
│       │   └── ServerDataSourceInitializer.java # 服务端初始化器
│       ├── service/                          # 业务逻辑层
│       │   ├── DataSourceManageService.java  # 全局数据源服务接口
│       │   ├── TenantDataSourceManageService.java # 租户数据源服务接口
│       │   └── impl/                         # 服务实现类
│       ├── util/
│       │   └── DataSourcePropertyUtils.java  # 数据源属性工具类
│       ├── DatasourceConfigure.java          # 模块配置类
│       ├── DatasourceConstant.java           # 常量定义
│       └── JdbcConfig.java                   # JDBC 配置接口
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 核心功能详解

### 1. 动态数据源管理 (Dynamic DataSource Management)

**主要功能：**
- 数据源的 CRUD 操作（创建、读取、更新、删除）
- 运行时启用/禁用数据源
- 数据库连接测试
- 与 DynamicDataSourceService 集成进行运行时同步

**核心类：**
- `DataSourceManageService` - 全局数据源业务逻辑服务
- `TenantDataSourceManageService` - 租户数据源业务逻辑服务
- `DataSourceController` - 全局数据源 API 控制器
- `TenantDataSourceController` - 租户数据源 API 控制器

### 2. 多租户数据源支持 (Multi-Tenant DataSource Support)

**数据源类型：**

#### 全局数据源 (Global DataSources)
- 存储在 `la_datasources` 表
- 所有租户可访问
- 直接 JDBC 属性配置

#### 租户特定数据源 (Tenant-Specific DataSources)
- 存储在 `la_tenant_datasource` 表
- 基于 `tenantId` 隔离
- JSON 配置格式，支持灵活的模式

**权限控制：**
- 管理员/默认租户：可访问所有数据源
- 普通租户：只能访问全局和自己的租户数据源
- 通过 `DubboContextHolder` 获取租户上下文

### 3. 分布式同步机制 (Distributed Synchronization)

**双模式架构：**

#### 服务端模式 (Server Mode) - 默认
- 从本地数据库加载数据源
- 提供 Dubbo RPC 服务
- 管理订阅者注册表
- 广播变更事件

#### 客户端模式 (Client Mode)
- 从远程 Dubbo 服务获取数据源
- 订阅变更通知
- 自动同步到本地 DynamicDataSourceService

**核心类：**
- `ServerDataSourceInitializer` - 服务端初始化器
- `ClientDataSourceInitializer` - 客户端初始化器
- `RemoteDataSourceService` - Dubbo RPC 服务接口
- `DataSourceChangeDispatcher` - 变更事件分发器

### 4. 事件驱动架构 (Event-Driven Architecture)

**本地事件：**
- `DataSourceEvent` - Spring ApplicationEvent
- 在数据源变更时发布
- 本地监听器处理

**远程事件：**
- `DataSourceChangeEvent` - 可序列化的 Dubbo 事件
- 变更类型：ADD, UPDATE, DELETE, ENABLE, DISABLE
- 通过 Dubbo 回调传播

**核心类：**
- `DataSourceChangeListener` - 回调接口
- `DataSourceChangeListenerImpl` - 客户端监听器实现
- `DataSourceEventListener` - 服务端事件监听器

## API 接口

### 全局数据源管理 API (`/datasource`)

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | `/page` | 分页查询数据源 | 登录用户 |
| GET | `/list` | 查询数据源列表 | 登录用户 |
| GET | `/{id}` | 查询数据源详情 | 登录用户 |
| POST | `/` | 新增数据源 | 登录用户 |
| PUT | `/{id}` | 更新数据源 | 登录用户 |
| DELETE | `/{id}` | 删除数据源 | 登录用户 |
| GET | `/{id}/test` | 测试数据源连接 | 登录用户 |
| PUT | `/{id}/enable` | 启用数据源 | 登录用户 |
| PUT | `/{id}/disable` | 禁用数据源 | 登录用户 |

### 租户数据源管理 API (`/tenant-datasource`)

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | `/list` | 查询租户数据源列表 | 登录用户 |
| GET | `/{id}` | 查询租户数据源详情 | 登录用户 |
| POST | `/` | 新增租户数据源 | 登录用户 |
| PUT | `/{id}` | 更新租户数据源 | 登录用户 |
| DELETE | `/{id}` | 删除租户数据源 | 登录用户 |

### Dubbo RPC 服务接口

| 方法 | 描述 |
|------|------|
| `listAll()` | 查询所有数据源 |
| `listEnabled()` | 查询所有启用的数据源 |
| `get(String id)` | 根据ID查询数据源 |
| `add(RemoteDataSource dto)` | 新增数据源 |
| `update(String id, RemoteDataSource dto)` | 更新数据源 |
| `delete(String id)` | 删除数据源 |
| `test(String id)` | 测试数据源连接 |
| `enable(String id)` | 启用数据源 |
| `disable(String id)` | 禁用数据源 |
| `subscribe(String clientId, DataSourceChangeListener callback)` | 订阅变更通知 |
| `unsubscribe(String clientId)` | 取消订阅 |

## 配置说明

### 数据源模块配置 (`lambda.fusion.datasource`)

```yaml
lambda:
  fusion:
    datasource:
      # 运行模式：server（服务端）或 client（客户端）
      mode: server  # 默认为 server
      
      # Dubbo 配置
      dubbo:
        group: datasource     # 服务分组
        version: 1.0.0       # 服务版本
```

### 模式配置说明

#### 服务端模式 (Server Mode)
```yaml
lambda:
  fusion:
    datasource:
      mode: server  # 或者不配置，默认为 server
```

**特点：**
- 从本地数据库加载数据源
- 提供 Dubbo RPC 服务
- 管理客户端订阅
- 适用于数据源管理中心

#### 客户端模式 (Client Mode)
```yaml
lambda:
  fusion:
    datasource:
      mode: client
```

**特点：**
- 从远程服务获取数据源
- 订阅变更通知
- 自动同步到本地
- 适用于业务应用服务

## 数据库表结构

### la_datasources 表（全局数据源表）

```sql
CREATE TABLE la_datasources (
  id VARCHAR(64) PRIMARY KEY,              -- 数据源编号
  datasource_name VARCHAR(255),            -- 数据源名称
  driver_class_name VARCHAR(255),          -- 驱动类名
  jdbc_url VARCHAR(1024),                  -- 连接地址
  username VARCHAR(255),                   -- 用户名
  password VARCHAR(255),                   -- 密码
  enabled INT DEFAULT 1                    -- 是否启用（0:禁用 1:启用）
);
```

### la_tenant_datasource 表（租户数据源表）

```sql
CREATE TABLE la_tenant_datasource (
  ID VARCHAR(64) PRIMARY KEY,              -- 主键
  DB_NAME VARCHAR(255),                    -- 数据库名称
  DB_DESC VARCHAR(500),                    -- 数据库描述
  DB_TYPE VARCHAR(50),                     -- 数据库类型
  CONFIGURATION TEXT,                      -- 配置信息（JSON格式）
  CREATE_TIME DATETIME,                    -- 创建时间
  UPDATE_TIME DATETIME,                    -- 更新时间
  CREATE_BY VARCHAR(64),                   -- 创建人
  ENABLED INT DEFAULT 1,                   -- 是否启用（0:禁用 1:启用）
  TENANT_ID VARCHAR(64),                   -- 租户ID
  MAPPING_OF VARCHAR(64)                   -- 映射关系
);
```

## 使用说明

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-datasource</artifactId>
    <version>2025.1.1-SNAPSHOT</version>
</dependency>
```

### 2. 启用框架

Lambda Fusion 模块使用 Spring Boot 自动配置机制，只需添加依赖即可自动启用：

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

模块会通过 `org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件自动加载配置。

### 3. 配置数据库

确保数据库连接配置正确，系统会自动创建所需的表结构。

## 开发指南

### 创建全局数据源

```java
// 通过 REST API 创建
POST /datasource
{
  "datasourceName": "业务数据库",
  "driverClassName": "com.mysql.cj.jdbc.Driver",
  "jdbcUrl": "jdbc:mysql://localhost:3306/business_db",
  "username": "root",
  "password": "password",
  "enabled": 1
}

// 通过 Dubbo 服务创建
RemoteDataSource dataSource = new RemoteDataSource();
dataSource.setDatasourceName("业务数据库");
dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/business_db");
dataSource.setUsername("root");
dataSource.setPassword("password");
dataSource.setEnabled(1);

boolean success = remoteDataSourceService.add(dataSource);
```

### 创建租户数据源

```java
// 通过 REST API 创建
POST /tenant-datasource
{
  "dbName": "租户A数据库",
  "dbDesc": "租户A专用数据库",
  "dbType": "mysql",
  "tenantId": "tenant-a",
  "enabled": 1,
  "configuration": {
    "jdbcUrl": "jdbc:mysql://localhost:3306/tenant_a_db",
    "username": "tenant_a",
    "password": "password",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }
}

// 通过 Dubbo 服务创建
RemoteDataSource dataSource = new RemoteDataSource();
dataSource.setDatasourceName("租户A数据库");
dataSource.setTenantId("tenant-a");
dataSource.setDbType("mysql");
dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/tenant_a_db");
dataSource.setUsername("tenant_a");
dataSource.setPassword("password");
dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
dataSource.setEnabled(1);

boolean success = remoteDataSourceService.add(dataSource);
```

### 测试数据源连接

```java
// 通过 REST API 测试
GET /datasource/{id}/test

// 通过 Dubbo 服务测试
boolean isConnected = remoteDataSourceService.test(dataSourceId);
```

### 启用/禁用数据源

```java
// 启用数据源
PUT /datasource/{id}/enable
// 或
remoteDataSourceService.enable(dataSourceId);

// 禁用数据源
PUT /datasource/{id}/disable
// 或
remoteDataSourceService.disable(dataSourceId);
```

### 订阅数据源变更

```java
// 客户端订阅变更通知
@Component
public class MyDataSourceChangeListener implements DataSourceChangeListener {
    
    @Override
    public void onDataSourceChanged(DataSourceChangeEvent event) {
        log.info("数据源变更: type={}, dataSourceId={}", 
                event.getChangeType(), event.getDataSourceId());
        
        switch (event.getChangeType()) {
            case ADD:
            case UPDATE:
            case ENABLE:
                // 处理数据源添加/更新/启用
                handleDataSourceUpdate(event.getDataSource());
                break;
            case DELETE:
            case DISABLE:
                // 处理数据源删除/禁用
                handleDataSourceRemove(event.getDataSourceId());
                break;
        }
    }
    
    private void handleDataSourceUpdate(RemoteDataSource dataSource) {
        // 更新本地数据源
    }
    
    private void handleDataSourceRemove(String dataSourceId) {
        // 移除本地数据源
    }
}

// 注册订阅
String clientId = "my-app-" + UUID.randomUUID().toString().substring(0, 8);
remoteDataSourceService.subscribe(clientId, new MyDataSourceChangeListener());

// 取消订阅
remoteDataSourceService.unsubscribe(clientId);
```

### 多租户数据源访问

```java
// 设置租户上下文（通常在拦截器中设置）
DubboContextHolder.setCurrentTenantId("tenant-a");

// 查询租户可访问的数据源（包括全局和租户特定）
List<RemoteDataSource> dataSources = remoteDataSourceService.listAll();

// 查询仅启用的数据源
List<RemoteDataSource> enabledDataSources = remoteDataSourceService.listEnabled();
```

### 自定义数据源变更处理

```java
@Component
public class CustomDataSourceEventListener {
    
    @EventListener
    public void handleDataSourceEvent(DataSourceEvent event) {
        RemoteDataSource dataSource = event.getDataSource();
        log.info("本地数据源事件: operation={}, dataSource={}", 
                event.getOperation(), dataSource.getDatasourceName());
        
        // 自定义处理逻辑
        if ("UPDATE".equals(event.getOperation())) {
            // 处理数据源更新
            processDataSourceUpdate(dataSource);
        } else if ("REMOVE".equals(event.getOperation())) {
            // 处理数据源删除
            processDataSourceRemove(dataSource);
        }
    }
    
    private void processDataSourceUpdate(RemoteDataSource dataSource) {
        // 自定义更新逻辑
    }
    
    private void processDataSourceRemove(RemoteDataSource dataSource) {
        // 自定义删除逻辑
    }
}
```

## 工作原理

### 数据源生命周期

```
数据源创建/更新
    ↓
保存到数据库
    ↓
同步到 DynamicDataSourceService
    ↓
发布 DataSourceEvent（本地）
    ↓
广播 DataSourceChangeEvent（远程）
    ↓
客户端接收回调通知
    ↓
客户端更新本地数据源
```

### 服务端模式初始化

1. **启动阶段**：`ServerDataSourceInitializer` 从数据库分页加载启用的数据源
2. **同步阶段**：将数据源注册到 `DynamicDataSourceService`
3. **服务阶段**：提供 Dubbo RPC 服务供客户端调用
4. **监听阶段**：监听数据源变更事件并广播给订阅者

### 客户端模式初始化

1. **连接阶段**：通过 Dubbo 连接到远程服务
2. **获取阶段**：调用 `listEnabled()` 获取初始数据源列表
3. **注册阶段**：将数据源注册到本地 `DynamicDataSourceService`
4. **订阅阶段**：订阅远程变更通知
5. **同步阶段**：接收变更回调并更新本地数据源

### 变更同步机制

1. **本地变更**：数据源 CRUD 操作触发本地事件
2. **事件发布**：`DataSourceEventListener` 监听本地事件
3. **远程广播**：通过 `DataSourceChangeDispatcher` 广播给订阅者
4. **客户端接收**：客户端回调接口接收变更通知
5. **本地同步**：客户端更新本地 `DynamicDataSourceService`