# Lambda Fusion Framework

## 项目简介

Lambda Fusion 是一个基于 Spring Boot 的微服务开发框架，提供了一套完整的企业级应用开发解决方案。该框架专注于权限管理、配置管理、数据字典、日志记录等核心业务功能的实现。

## 项目架构

项目采用模块化设计，包含以下核心模块：

```
lambda-fusion-parent/
├── lambda-fusion-bom/          # BOM 依赖管理
├── lambda-fusion-core/         # 核心框架
├── lambda-fusion-authority/    # 权限管理模块
├── lambda-fusion-config/       # 配置管理模块
├── lambda-fusion-dict/         # 数据字典模块
├── lambda-fusion-log/          # 日志管理模块
├── lambda-fusion-datasource/   # 数据源模块
└── lambda-fusion-extension/    # 扩展模块
    └── lambda-fusion-api-permission/ # API 权限控制
```

## 核心功能

### 1. 权限管理系统 (lambda-fusion-authority)

提供完整的企业级权限管理解决方案：

- **用户管理**: 用户信息、在线状态、密码管理
- **角色管理**: 角色权限分配、角色组管理
- **组织架构**: 多层级组织结构支持
- **资源管理**: 菜单、按钮、API 资源权限控制
- **客户端管理**: API Token 管理、客户端授权
- **多租户支持**: 租户配置、租户数据隔离
- **认证服务**: 用户认证、导航菜单生成

**主要特性**：
- 基于 RBAC 的权限模型
- 支持多租户架构
- 缓存优化（Redis/Caffeine）
- 异步操作日志记录
- 动态权限验证

### 2. 核心框架 (lambda-fusion-core)

框架核心功能库：

- **AbstractCrudService**: 通用 CRUD 服务基类，支持实体与 VO 自动转换
- **TreeBuilder**: 树形结构构建工具
- **@LambdaFusionApplication**: 框架启动注解
- **分页支持**: 统一的分页查询封装
- **类型转换**: 基于 MapStruct 的对象转换

**核心功能点**：
- 泛型化的 CRUD 操作
- 自动化的实体转换
- 树形数据结构处理
- 统一的分页机制

### 3. 配置管理 (lambda-fusion-config)

动态配置管理系统：

- **数据库配置源**: 基于数据库的配置存储
- **配置热刷新**: 支持配置实时更新
- **Nacos 集成**: 支持 Nacos 配置中心
- **配置版本管理**: 配置变更历史追踪

### 4. 数据字典 (lambda-fusion-dict)

统一的数据字典管理：

- **字典类型管理**: 字典分类组织
- **字典项管理**: 字典值的 CRUD 操作
- **动态字典**: 支持 SQL 查询、URL 接口等动态数据源
- **缓存支持**: 字典数据缓存优化

## 技术栈

### 核心技术

- **Java**: JDK 21+
- **Spring Boot**: 微服务框架
- **Spring Security**: 安全框架
- **MyBatis Plus**: ORM 框架
- **Spring Cloud**: 微服务生态

### 数据存储

- **MySQL/PostgreSQL**: 关系型数据库
- **Redis**: 缓存存储
- **Liquibase**: 数据库版本管理

### 其他依赖

- **MapStruct**: 对象映射
- **Lombok**: 代码简化
- **Nacos**: 配置中心/注册中心
- **Caffeine**: 本地缓存
- **Hutool**: 工具库

## 快速开始

### 1. 环境要求

- JDK 21 或更高版本
- Maven 3.6+
- MySQL 8.0+ 或 PostgreSQL 12+
- Redis 5.0+

### 2. 项目构建

```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd lambda-fusion-parent

# 构建项目
mvn clean install
```

### 3. 应用启动

在你的 Spring Boot 主类中使用 `@LambdaFusionApplication` 注解：

```java
@LambdaFusionApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 配置示例

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lambda_fusion
    username: root
    password: password
  
  redis:
    host: localhost
    port: 6379
    
lambda:
  fusion:
    authority:
      tenant:
        enabled: true
    config:
      auto-refresh:
        enabled: true
```

## 模块使用指南

### 权限管理模块

```java
// 继承 AbstractCrudService 实现 CRUD 操作
@Service
public class UserService extends AbstractCrudService<UserEntity, UserVO, UserMapper> {
    
    // 获取用户 VO 分页数据
    public IPage<UserVO> getUserPage(IPage<UserEntity> page, QueryWrapper<UserEntity> wrapper) {
        return pageForVO(page, wrapper);
    }
}
```

### 配置管理模块

```java
// 配置变更监听
@Component
public class ConfigChangeListener implements ConfigChangedService {
    @Override
    public void onConfigChanged(String key, String value) {
        // 处理配置变更逻辑
    }
}
```

### 数据字典模块

```java
// 使用字典解析
@Service
public class DictService {
    
    @Autowired
    private IDynamicDictResolve dictResolve;
    
    public List<DictInfo> getDictData(String dictType) {
        return dictResolve.resolve(dictType);
    }
}
```

## 开发规范

### 代码结构

```
com/lambda/fusion/module/
├── controller/     # 控制器层
├── service/        # 业务逻辑层
│   └── impl/      # 实现类
├── mapper/         # 数据访问层
├── model/          # 数据模型
│   ├── entity/    # 实体类
│   ├── dto/       # 数据传输对象
│   └── vo/        # 视图对象
└── config/         # 配置类
```

### 命名规范

- **Entity**: 以 `Entity` 结尾，如 `UserEntity`
- **DTO**: 以 `DTO` 结尾，如 `UserCreateDTO`
- **VO**: 以 `VO` 结尾，如 `UserVO`
- **Service**: 以 `Service` 结尾，如 `UserService`
- **Mapper**: 以 `Mapper` 结尾，如 `UserMapper`

## 版本信息

- **当前版本**: 2025.1.1-SNAPSHOT
- **父项目**: lambda-cloud-parent

## 许可证

本项目采用 [Apache License 2.0](./LICENSE) 开源许可证。

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/新功能`)
3. 提交变更 (`git commit -am '添加新功能'`)
4. 推送到分支 (`git push origin feature/新功能`)
5. 创建 Pull Request

## 技术支持

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 邮箱联系开发团队

---

**Lambda Fusion Framework** - 企业级微服务开发框架