# Lambda Fusion Authority

Lambda Fusion Authority 是 Lambda Fusion 框架中的企业级权限管理模块，基于 Lambda Cloud 构建，提供完整的 RBAC（基于角色的访问控制）解决方案，支持多租户、用户管理、角色管理、资源授权和身份认证服务。

## 项目概述

本项目是 Lambda Fusion 框架的核心权限管理模块，专为企业级微服务应用设计，提供统一的权限管理、用户认证、角色授权等功能。作为 Lambda Cloud 生态的一部分，与其他模块无缝集成。

### 核心特性

- **RBAC 权限模型**：基于角色的访问控制，支持层级权限管理
- **多租户支持**：完整的租户隔离，独立的数据和配置
- **用户管理**：用户增删改查、密码管理、在线状态跟踪
- **角色管理**：角色分组、权限分配、批量用户分配
- **资源管理**：菜单、按钮、API 接口的层级化权限控制
- **组织架构**：多级组织结构管理
- **缓存策略**：多级缓存（Redis + Caffeine）提升性能
- **审计日志**：操作日志和用户活动跟踪
- **国际化支持**：多语言资源支持
- **Sa-Token 集成**：基于 Sa-Token 的轻量级认证授权
- **动态权限验证**：实时权限校验和菜单生成

## 技术栈

### 核心技术
- **Java 21+**
- **Spring Boot 3.x**
- **Sa-Token** - 轻量级认证授权框架
- **MyBatis Plus** - ORM 框架
- **Lambda Cloud** - 基础框架依赖

### 数据存储
- **MySQL/PostgreSQL** - 关系型数据库
- **Redis** - 缓存和会话存储
- **Liquibase** - 数据库版本管理

### Lambda Cloud Starter 集成
- **lambda-cloud-starter-security** - 安全框架（包含 Sa-Token）
- **lambda-cloud-starter-mybatis** - MyBatis 集成
- **lambda-cloud-starter-redis** - Redis 集成
- **lambda-cloud-starter-cache** - 缓存管理（Caffeine）
- **lambda-cloud-starter-datasource** - 数据源管理
- **lambda-cloud-starter-logger** - 日志框架
- **lambda-cloud-starter-sms** - 短信服务
- **lambda-cloud-starter-sse** - 服务端推送
- **lambda-cloud-starter-liquibase** - 数据库版本管理
- **lambda-cloud-starter-nacos** - 配置中心

### 工具库
- **MapStruct** - 对象映射
- **Lombok** - 代码生成
- **Hutool** - 工具库
- **Caffeine** - 本地缓存

## 项目结构

```
lambda-fusion-authority/
├── src/main/java/com/lambda/fusion/authority/
│   ├── user/              # 用户管理模块
│   │   ├── controller/    # 用户相关 API 控制器
│   │   ├── service/       # 用户业务逻辑服务
│   │   ├── mapper/        # 用户数据访问层
│   │   ├── model/         # 用户数据模型
│   │   └── helper/        # 用户辅助工具类
│   ├── role/              # 角色管理模块
│   │   ├── controller/    # 角色相关 API 控制器
│   │   ├── service/       # 角色业务逻辑服务
│   │   ├── mapper/        # 角色数据访问层
│   │   └── model/         # 角色数据模型
│   ├── resource/          # 资源权限管理模块
│   │   ├── controller/    # 资源相关 API 控制器
│   │   ├── service/       # 资源业务逻辑服务
│   │   ├── mapper/        # 资源数据访问层
│   │   └── model/         # 资源数据模型
│   ├── organization/      # 组织架构管理模块
│   │   ├── controller/    # 组织相关 API 控制器
│   │   ├── service/       # 组织业务逻辑服务
│   │   ├── mapper/        # 组织数据访问层
│   │   └── domain/        # 组织领域模型
│   ├── tenant/            # 多租户支持模块
│   │   ├── controller/    # 租户相关 API 控制器
│   │   ├── service/       # 租户业务逻辑服务
│   │   ├── mapper/        # 租户数据访问层
│   │   ├── model/         # 租户数据模型
│   │   ├── manager/       # 租户管理器
│   │   ├── event/         # 租户事件处理
│   │   └── cache/         # 租户缓存
│   ├── authentication/    # 认证授权模块
│   │   ├── controller/    # 认证相关 API 控制器
│   │   ├── service/       # 认证业务逻辑服务
│   │   ├── mapper/        # 认证数据访问层
│   │   └── model/         # 认证数据模型
│   ├── client/            # API 客户端管理模块
│   ├── token/             # 令牌管理模块
│   ├── area/              # 区域管理模块
│   ├── AuthorityConfigure.java    # 权限模块配置类
│   └── AuthorityConstants.java    # 权限常量定义
├── src/main/resources/
│   ├── mapper/            # MyBatis XML 映射文件
│   └── templates/         # HTML 模板文件
└── pom.xml
```

## 核心模块详解

### 1. 用户管理模块 (User Management)

**主要功能：**
- 用户 CRUD 操作
- 密码管理（修改、重置，支持可配置策略）
- 用户在线状态跟踪
- 用户资料和扩展字段管理
- 组织绑定
- 多租户用户隔离
- 用户锁定/解锁功能
- 批量用户操作

**核心类：**
- `UserService` - 用户业务逻辑服务
- `UserController` - 用户 API 控制器
- `User` - 用户数据模型
- `UserServiceImpl` - 用户服务实现

### 2. 角色管理模块 (Role Management)

**主要功能：**
- 角色 CRUD 操作
- 角色分组和组织
- 权限授予/撤销
- 内置角色支持（ROLE_SYSTEM, ROLE_ADMIN, ROLE_DEV, ROLE_USER, ROLE_MANAGER, ROLE_ORG）
- 角色禁用/启用
- 批量用户角色分配
- 角色组管理

**核心类：**
- `RoleService` - 角色业务逻辑服务
- `RoleController` - 角色 API 控制器
- `Role` - 角色数据模型
- `RoleServiceImpl` - 角色服务实现

### 3. 资源管理模块 (Resource Management)

**资源类型：**
- **Interface** - API 接口
- **Menu** - UI 菜单
- **Button** - UI 按钮
- **External Link** - 外部链接
- **Embedded Page** - 内嵌页面

**主要功能：**
- 层级化资源组织
- 资源权限分配
- 资源模式支持（后台/应用资源）
- 国际化支持

### 4. 认证授权模块 (Authentication & Authorization)

**主要功能：**
- 用户名/手机号登录
- 基于 Sa-Token 的会话管理
- 基于角色的导航菜单生成
- 用户资料获取
- 权限列表管理
- 用户详情丰富化
- 登录状态监听和处理

**核心类：**
- `AuthenticationService` - 认证业务逻辑服务
- `AuthenticationServiceImpl` - 认证服务实现
- `AuthenticationController` - 认证 API 控制器

**Sa-Token 集成：**
- 使用 `@SaCheckLogin` 进行登录校验
- 使用 `@SaCheckRole` 进行角色权限校验
- 通过 `StpUtil` 进行会话管理
- 实现 `SaTokenListener` 监听登录事件

### 5. 组织管理模块 (Organization Management)

**主要功能：**
- 多级组织结构
- 父子关系管理
- 组织所有权
- 用户-组织绑定
- 基于组织的权限过滤

### 6. 多租户模块 (Tenant Management)

**主要功能：**
- 租户特定数据隔离
- 租户数据库初始化
- 租户授权管理
- 基于域名的租户识别
- 租户用户管理

## API 接口

### 用户管理 API (`/authority/users`)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/` | 分页查询用户列表 |
| GET | `/{username}` | 获取用户详情 |
| GET | `/{username}/check` | 检查用户名是否存在 |
| POST | `/` | 创建用户 |
| PUT | `/{username}` | 更新用户信息 |
| DELETE | `/{username}` | 删除用户 |
| PUT | `/password/edit` | 修改密码 |
| PUT | `/password/reset` | 重置密码 |
| PATCH | `/{username}/disabled` | 禁用用户 |
| PATCH | `/{username}/enabled` | 启用用户 |
| PATCH | `/{username}/unlock` | 解锁用户 |
| GET | `/{username}/permission` | 获取用户权限 |

### 角色管理 API (`/authority/roles`)

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/` | 获取所有角色列表 |
| GET | `/grouped` | 获取分组角色列表 |
| GET | `/page/{number}/size/{size}` | 分页查询角色 |
| POST | `/` | 创建角色 |
| PUT | `/{authority}` | 更新角色 |
| DELETE | `/{authority}` | 删除角色 |
| PATCH | `/{authority}/disabled` | 禁用角色 |
| PATCH | `/{authority}/enabled` | 启用角色 |
| GET | `/{authority}/permissions` | 获取角色权限 |
| PUT | `/{authority}/grant/{resourceId}` | 授予权限 |
| DELETE | `/{authority}/grant/{resourceId}` | 撤销权限 |
| POST | `/assignUsers` | 批量分配用户到角色 |

## 配置说明

### 权限配置 (`lambda.fusion.authorize`)

```yaml
lambda:
  fusion:
    authorize:
      # 是否启用数据级角色控制
      enabledDataRole: false
      # 使用组织名称作为 ID
      useOrgNameAsId: false
      # 密码策略配置
      passwordStrategy:
        # 密码策略模式：RANDOM(随机), FIXED(固定), CIPHERTEXT(密文)
        mode: RANDOM
        # 固定密码值（当模式为 FIXED 时生效）
        customize: "123456"
        # 是否启用密码定期更换
        enablePeriodChange: false
        # 密码有效期（天）
        periodChangeDays: 90
      # 开发者角色配置
      dev:
        # 开发者角色白名单
        whiteList: []
```

## 依赖集成

本项目基于 Lambda Cloud 框架构建，集成了以下 Lambda Cloud Starter 库：

- `lambda-fusion-core` - 框架核心库
- `lambda-cloud-starter-security` - 安全框架（集成 Sa-Token）
- `lambda-cloud-starter-logger` - 日志框架
- `lambda-cloud-starter-sms` - 短信服务
- `lambda-cloud-starter-datasource` - 数据源管理
- `lambda-cloud-starter-mybatis` - MyBatis 集成
- `lambda-cloud-starter-redis` - Redis 集成
- `lambda-cloud-starter-liquibase` - 数据库版本管理
- `lambda-cloud-starter-nacos` - 配置中心
- `lambda-cloud-starter-sse` - 服务端推送
- `lambda-cloud-starter-cache` - 缓存管理

### 外部依赖
- `caffeine` - 本地缓存实现

## 开发指南

### 权限注解使用

使用 Sa-Token 提供的权限注解进行接口保护：

```java
@RestController
public class UserController {
    
    // 登录校验
    @SaCheckLogin
    @GetMapping("/users")
    public List<User> getUsers() {
        return userService.getAllUsers();
    }
    
    // 角色校验
    @SaCheckRole("ROLE_ADMIN")
    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }
}
```

### 扩展用户字段

可以通过 `UserFields` 机制扩展用户属性：

```java
// 在用户创建时添加自定义字段
Map<String, Object> customFields = new HashMap<>();
customFields.put("department", "技术部");
customFields.put("position", "高级工程师");
user.setPersonal(customFields);
```

### 继承 AbstractCrudService

利用框架提供的通用 CRUD 服务：

```java
@Service
public class CustomUserService extends AbstractCrudService<UserEntity, UserVO, UserMapper> {
    
    // 获取用户 VO 分页数据
    public IPage<UserVO> getUserPage(IPage<UserEntity> page, QueryWrapper<UserEntity> wrapper) {
        return pageForVO(page, wrapper);
    }
}
```

### 多租户数据隔离

系统自动根据当前用户的租户 ID 进行数据隔离，无需额外配置。

### 监听用户登录事件

实现 Sa-Token 监听器处理用户登录事件：

```java
@Component
public class UserLoginListener extends SaTokenListenerForSimple {
    
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginModel loginModel) {
        // 处理用户登录逻辑
        log.info("用户登录: {}", loginId);
    }
    
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        // 处理用户登出逻辑
        log.info("用户登出: {}", loginId);
    }
}
```