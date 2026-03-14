# Lambda Fusion Framework

## 项目简介

Lambda Fusion 是一个基于 [**lamuda-cloud-parent**](https://gitee.com/lamuda-cloud/lamuda-cloud-parent) 的业务开发框架，专注于权限管理、配置管理、数据字典、日志记录等核心业务功能的实现。

## 项目架构

项目采用模块化设计，包含以下核心模块：

```
lambda-fusion-parent/
├── lambda-fusion-AI/           # AI知识库管理
├── lambda-fusion-bom/          # BOM 依赖管理
├── lambda-fusion-core/         # 核心框架
├── lambda-fusion-authority/    # 权限管理模块
├── lambda-fusion-config/       # 配置管理模块
├── lambda-fusion-dictionary/   # 数据字典模块
├── lambda-fusion-upload/       # 上传文件模块
├── lambda-fusion-datasource/   # 数据源模块
└── lambda-fusion-permission/   # API 权限控制
```

## 核心功能

### 1. 权限管理 (lambda-fusion-authority)

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

### 2. Core模块 (lambda-fusion-core)

框架核心功能库：

- **AbstractCrudService**: 通用 CRUD 服务基类，支持实体与 VO 自动转换
- **TreeBuilder**: 树形结构构建工具
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
- **Sa-Token**: 安全框架
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