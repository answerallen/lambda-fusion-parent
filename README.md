<div align="center">

# 🚀 Lambda Fusion Framework

**基于 Spring Boot 4.0 + JDK 21 构建的全栈企业级微服务开发框架**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.x-brightblue.svg)](https://spring.io/projects/spring-cloud)
[![JDK](https://img.shields.io/badge/JDK-21+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
   
</div>

---

## 📋 项目简介

Lambda Fusion 是一个基于 [**lamuda-cloud-parent**](https://gitee.com/westboy/lamuda-cloud-parent) 的企业级业务开发框架，提供开箱即用的权限管理、配置管理、数据字典、AI 智能平台等核心业务功能，助力快速构建微服务应用。

### ✨ 核心特性

- 🔐 **完善的权限体系** - RBAC 模型 + 多租户支持
- ⚙️ **动态配置管理** - 支持热更新和多配置源
- 📚 **灵活的数据字典** - 静态/动态字典，支持多种数据源
- 🤖 **AI 智能平台** - RAG知识库、Agent工作流、AI机器人
- 🎯 **开箱即用** - 预置常用业务模块，快速启动项目


## 🏗️ 项目架构

采用模块化设计，各模块职责清晰，可按需引入：

```
lambda-fusion-parent/
├── 📦 lambda-fusion-bom/          # BOM 依赖管理
├── 🤖 lambda-fusion-ai/           # AI模块
├── 🎯 lambda-fusion-core/         # 核心模块
├── 🔐 lambda-fusion-authority/    # 权限管理（用户、角色、组织、资源）
├── ⚙️ lambda-fusion-config/       # 配置管理（动态配置、热更新）
├── 📚 lambda-fusion-dictionary/   # 数据字典（静态/动态字典）
├── 📤 lambda-fusion-oss/       # 文件上传（OSS、本地存储）
├── 🗄️ lambda-fusion-datasource/   # 数据源管理（多数据源支持）
└── 🛡️ lambda-fusion-permission/   # 权限控制模块
    ├── lambda-fusion-permission-api/  # API接口权限
    └── ️lambda-fusion-permission-datascope/   # 数据权限
```


## 🎯 核心功能
| 项目 | 说明 | 
|------|---------|
| 👤 **用户管理** | 用户信息维护、在线状态监控、密码策略管理 |
| 🎭 **角色管理** | 角色权限分配、角色组管理、角色继承 |
| 🏢 **组织架构** | 多层级组织结构、部门管理、岗位管理 |
| 📋 **资源管理** | 菜单权限、按钮权限、API 资源控制 |
| 🔑 **客户端管理** | 客户端管理、授权、访问控制 |
| 🏠 **多租户支持** | 租户配置、数据隔离、租户级权限 |
| 🔒 **认证服务** | 用户认证、SSO 支持、动态菜单生成 |
| 🤖 **RAG 知识库** | 向量检索、混合检索、文档理解、多租户隔离 |
| 🤖 **Agent 工作流** | 可视化编排、节点扩展、条件路由、动态执行 |
| 🤖 **AI 机器人** | 机器人管理、人设配置、知识库绑定 |


## 🛠️ 技术栈

| 技术 | 版本     | 说明 |
|------|--------|------|
| ☕ **Java** | 21+    | 最新 LTS 版本，性能优异 |
| 🍃 **Spring Boot** | 4.0.2  | 微服务开发框架 |
| ☁️ **Spring Cloud** | Latest | 微服务生态组件 |
| 🔐 **Sa-Token** | Latest | 轻量级权限认证框架 |
| 💾 **MyBatis Plus** | Latest | 增强版 ORM 框架 |
| 🔄 **MapStruct** | Latest |高性能对象映射 |
| 🌶️ **Lombok** |Latest | 简化 Java 代码 |
| ☁️ **Nacos** |Latest | 配置中心 / 注册中心 |
| ☕ **Caffeine** |Latest | 高性能本地缓存 |
| 🔧 **Hutool** |Latest | Java 工具类库 |


## 🎨 预览与体验

* **演示地址**：[http://lambda.devcms.cn:20005/index.html](http://lambda.devcms.cn:20005/index.html)

* **账号密码**：`westboy` / `8a30d075d80fad0e799a6ac3a654a214`

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

---

## 📖 生态依赖

- **[lambda-cloud-parent](https://gitee.com/westboy/lambda-cloud-parent)** - 核心基座，封装底层自动化配置与基础工具类
- **[lambda-cloud-project-parent](https://gitee.com/westboy/lambda-cloud-project-parent)** - 统管项目依赖版本与 Maven 构建标准
- **[lambda-fusion-web](https://gitee.com/westboy/lambda-fusion-web)** - 基于 Vben Admin 构建的现代化前端界面


## 📚 相关资源

- 🎯 [实战项目示例](https://gitee.com/westboy/lambda-fusion-admin)


## 📄 开源协议

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源协议。

**⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！**❤️
