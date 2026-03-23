<div align="center">

# 🚀 Lambda Fusion Framework

**企业级微服务业务开发框架**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.x-brightblue.svg)](https://spring.io/projects/spring-cloud)
[![JDK](https://img.shields.io/badge/JDK-21+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[>>> 实战示例项目：https://gitee.com/westboy/lambda-fusion-example-project <<<](https://gitee.com/westboy/lambda-fusion-example-project) 
   
</div>

---

## 📋 项目简介

Lambda Fusion 是一个基于 [**lamuda-cloud-parent**](https://gitee.com/westboy/lamuda-cloud-parent) 的企业级业务开发框架，提供开箱即用的权限管理、配置管理、数据字典、Rag知识库等核心业务功能，助力快速构建微服务应用。

### ✨ 核心特性

- 🔐 **完善的权限体系** - RBAC 模型 + 行级数据权限 (DataScope) + 多租户支持
- ⚙️ **动态配置管理** - 支持热更新和多配置源
- 📚 **灵活的数据字典** - 静态/动态字典，支持基于 SQL 的数据源
- 🤖 **AI 知识库集成** - RAG 文档检索、向量化存储与 LLM 会话支持
- 🎯 **开箱即用** - 预置常用业务模块，快速启动项目


## 🏗️ 项目架构

采用模块化设计，各模块职责清晰，可按需独立引入：

```text
lambda-fusion-parent/
├── 📦 lambda-fusion-bom/          # BOM 依赖版本管理
├── 🎯 lambda-fusion-core/         # 核心基础模块（通用实体、树组件、分页、工具类）
├── 🔐 lambda-fusion-authority/    # 权限与用户中心（用户、角色、组织、资源、租户、登录认证）
├── 🛡️ lambda-fusion-permission/   # 权限控制引擎（API 接口鉴权、DataScope 数据隔离）
├── ⚙️ lambda-fusion-config/       # 动态配置管理（全局设置、系统配置热加载）
├── 📚 lambda-fusion-dictionary/   # 数据字典服务（静态/动态字典引擎）
├── 🗄️ lambda-fusion-datasource/   # 多数据源管理（基于多租户的动态数据源路由）
├── 📤 lambda-fusion-oss/          # 对象存储服务（统一附件管理）
├── 🤖 lambda-fusion-ai/           # AI 智能化模块（RAG、文档切片、向量检索、LLM）
└── 🚀 lambda-fusion-startup/      # 框架启动器（融合启动验证与测试工程）
```

## 🎯 核心功能

| 项目 | 说明 | 
|------|---------|
| 👤 **用户管理** | 用户信息维护、在线状态监控、密码策略管理 |
| 🎭 **角色管理** | 角色权限分配、角色组管理、角色继承 |
| 🏢 **组织架构** | 多层级组织结构、部门管理、岗位管理 |
| 📋 **资源与权限** | 菜单权限、按钮权限、API 接口级安全控制 |
| 🛡️ **数据权限** | 行级数据隔离（DataScope）、泛化主体授权、智能层级同步 |
| 🏠 **多租户支持** | 租户配置、数据隔离、多租户动态数据源路由 |
| 🔒 **认证服务** | 用户认证登录、SSO 支持、Token 管理 |
| 📚 **数据字典** | 支持静态枚举配置与基于 SQL 的动态字典查询 |
| ⚙️ **配置中心** | 系统全局设置、动态配置下发与实时生效 |
| 🤖 **AI 知识库** | 文档解析、向量化存储、RAG 增强检索与大模型问答 |
| 📤 **文件存储** | 统一附件上传与分组管理，支持多对象存储 |
| 🔑 **客户端管理** | 内部调用与外部 API 客户端授权、访问控制 |


## 🛠️ 技术栈

### 核心框架

| 技术 | 版本     | 说明 |
|------|--------|------|
| ☕ **Java** | 21+    | 最新 LTS 版本，性能优异 |
| 🍃 **Spring Boot** | 4.0.2  | 微服务开发框架 |
| ☁️ **Spring Cloud** | Latest | 微服务生态组件 |
| 🔐 **Sa-Token** | Latest | 轻量级权限认证框架 |
| 💾 **MyBatis Plus** | Latest | 增强版 ORM 框架 |
| 🌳 **JSqlParser** | Latest | 强大的 SQL 语法树解析器（用于动态数据权限） |


### 数据存储

| 技术 | 用途 |
|------|------|
| 🐬 **MySQL / PostgreSQL** | 关系型数据库 |
| 🔴 **Redis** | 缓存存储、分布式锁 |
| 📊 **Liquibase** | 数据库版本管理 |
| 🧠 **Vector DB** | 向量数据库（AI 模块知识检索） |

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
<!-- 权限与用户管理 -->
<dependency>
    <groupId>com.lambda.fusion</groupId>
    <artifactId>lambda-fusion-authority</artifactId>
</dependency>

<!-- 数据权限控制面 -->
<dependency>
    <groupId>com.lambda.fusion</groupId>
    <artifactId>lambda-fusion-permission-datascope</artifactId>
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

- 🎯 [实战项目示例](https://gitee.com/westboy/lambda-fusion-example-project)


## 📄 开源协议

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源协议。

**⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！**❤️
