<div align="center">

# 🚀 Lambda Fusion Framework

**企业级微服务业务开发框架**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JDK](https://img.shields.io/badge/JDK-21+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

 [🎯 示例项目](https://gitee.com/westboy/lambda-fusion-admin) | [🔧 开发组件](https://gitee.com/westboy/lamuda-cloud-parent)

</div>

---

## 📋 项目简介

Lambda Fusion 是一个基于 [**lamuda-cloud-parent**](https://gitee.com/westboy/lamuda-cloud-parent) 的企业级业务开发框架，提供开箱即用的权限管理、配置管理、数据字典、Rag知识库等核心业务功能，助力快速构建微服务应用。

### ✨ 核心特性

- 🔐 **完善的权限体系** - RBAC 模型 + 多租户支持
- ⚙️ **动态配置管理** - 支持热更新和多配置源
- 📚 **灵活的数据字典** - 静态/动态字典，支持多种数据源
- 🤖 **AI 知识库集成** - 智能化业务支持
- 🎯 **开箱即用** - 预置常用业务模块，快速启动项目


## 🏗️ 项目架构

采用模块化设计，各模块职责清晰，可按需引入：

```
lambda-fusion-parent/
├── 📦 lambda-fusion-bom/          # BOM 依赖管理
├── 🎯 lambda-fusion-core/         # 核心框架（CRUD、树形结构、分页等）
├── 🔐 lambda-fusion-authority/    # 权限管理（用户、角色、组织、资源）
├── ⚙️ lambda-fusion-config/       # 配置管理（动态配置、热更新）
├── 📚 lambda-fusion-dictionary/   # 数据字典（静态/动态字典）
├── 📤 lambda-fusion-upload/       # 文件上传（OSS、本地存储）
├── 🗄️ lambda-fusion-datasource/   # 数据源管理（多数据源支持）
├── 🛡️ lambda-fusion-permission/   # API 权限控制（接口级权限）
└── 🤖 lambda-fusion-AI/           # AI 知识库管理
```

## 🎯 核心功能

### 🔐 权限管理 (lambda-fusion-authority)

提供完整的企业级权限管理解决方案，支持复杂的权限控制场景。

#### 功能模块

| 模块 | 功能描述 |
|------|---------|
| 👤 **用户管理** | 用户信息维护、在线状态监控、密码策略管理 |
| 🎭 **角色管理** | 角色权限分配、角色组管理、角色继承 |
| 🏢 **组织架构** | 多层级组织结构、部门管理、岗位管理 |
| 📋 **资源管理** | 菜单权限、按钮权限、API 资源控制 |
| 🔑 **客户端管理** | API Token 管理、客户端授权、访问控制 |
| 🏠 **多租户支持** | 租户配置、数据隔离、租户级权限 |
| 🔒 **认证服务** | 用户认证、SSO 支持、动态菜单生成 |

#### 核心特性

- ✅ 基于 RBAC 的权限模型，支持细粒度权限控制
- ✅ 完善的多租户架构，数据完全隔离
- ✅ 多级缓存优化（Redis + Caffeine），性能卓越
- ✅ 异步操作日志记录，不影响业务性能
- ✅ 动态权限验证，支持运行时权限变更


### 🎯 Core 模块 (lambda-fusion-core)

框架核心功能库，提供通用的业务开发能力。

#### 核心组件

- **AbstractCrudService**: 通用 CRUD 服务基类
  - 自动实体与 VO 转换
  - 统一的增删改查接口
  - 支持批量操作
  
- **TreeBuilder**: 树形结构构建工具
  - 支持任意层级树形数据
  - 自动父子关系处理
  - 高性能构建算法

- **分页支持**: 统一的分页查询封装
  - MyBatis Plus 集成
  - 自动分页参数处理
  - 支持多种排序方式

- **类型转换**: 基于 MapStruct 的对象转换
  - 编译期类型安全
  - 高性能零反射
  - 支持复杂对象映射


### ⚙️ 配置管理 (lambda-fusion-config)

动态配置管理系统，支持配置实时更新和多配置源。

#### 功能特性

| 特性 | 说明 |
|------|------|
| 🗄️ **数据库配置源** | 基于数据库的配置存储，支持 MySQL/PostgreSQL |
| 🔄 **配置热刷新** | 配置实时更新，无需重启应用 |
| ☁️ **Nacos 集成** | 支持 Nacos 配置中心，云原生配置管理 |
| 📝 **版本管理** | 配置变更历史追踪，支持回滚 |
| 🔔 **变更通知** | 配置变更事件通知，支持自定义监听器 |


### 📚 数据字典 (lambda-fusion-dictionary)

统一的数据字典管理，支持静态和动态字典。

#### 字典类型

- **静态字典**: 预定义的固定字典项
- **动态字典**: 支持多种数据源
  - SQL 查询
  - HTTP 接口
  - 自定义数据源

#### 核心功能

- ✅ 字典类型分类管理
- ✅ 字典项 CRUD 操作
- ✅ 多级字典支持
- ✅ 字典数据缓存优化
- ✅ 国际化支持


## 🛠️ 技术栈

### 核心框架

| 技术 | 版本     | 说明 |
|------|--------|------|
| ☕ **Java** | 21+    | 最新 LTS 版本，性能优异 |
| 🍃 **Spring Boot** | 4.0.2  | 微服务开发框架 |
| 🔐 **Sa-Token** | Latest | 轻量级权限认证框架 |
| 💾 **MyBatis Plus** | Latest | 增强版 ORM 框架 |
| ☁️ **Spring Cloud** | Latest | 微服务生态组件 |

### 数据存储

| 技术 | 用途 |
|------|------|
| 🐬 **MySQL / PostgreSQL** | 关系型数据库 |
| 🔴 **Redis** | 缓存存储、分布式锁 |
| 📊 **Liquibase** | 数据库版本管理 |

### 工具库

| 技术 | 说明 |
|------|------|
| 🔄 **MapStruct** | 高性能对象映射 |
| 🌶️ **Lombok** | 简化 Java 代码 |
| ☁️ **Nacos** | 配置中心 / 注册中心 |
| ☕ **Caffeine** | 高性能本地缓存 |
| 🔧 **Hutool** | Java 工具类库 |


## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+ / PostgreSQL 12+
- Redis 6.0+

### 引入依赖

在项目 `pom.xml` 中添加：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.lambda.fusion</groupId>
            <artifactId>lambda-fusion-bom</artifactId>
            <version>${lambda-fusion.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 使用模块

根据需要引入具体模块：

```xml
<!-- 权限管理 -->
<dependency>
    <groupId>com.lambda.fusion</groupId>
    <artifactId>lambda-fusion-authority</artifactId>
</dependency>

<!-- 配置管理 -->
<dependency>
    <groupId>com.lambda.fusion</groupId>
    <artifactId>lambda-fusion-config</artifactId>
</dependency>

<!-- 数据字典 -->
<dependency>
    <groupId>com.lambda.fusion</groupId>
    <artifactId>lambda-fusion-dictionary</artifactId>
</dependency>
```


## 📚 相关资源

- 🎯 [实战项目示例](https://gitee.com/westboy/lambda-fusion-admin)
- 🔧 [基础框架 lamuda-cloud-parent](https://gitee.com/westboy/lamuda-cloud-parent)
- 📖 [在线文档](https://gitee.com/westboy/lambda-fusion-admin)


## 📄 开源协议

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源协议。

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！**❤️


</div>