<div align="center">

<p align="center">
	<img alt="logo" src="assets/logo.png" width="250" height="250">
</p>

# 🚀 Lambda Fusion Framework

**基于 lambda-cloud 构建的全栈企业级微服务业务开发框架**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.x-brightblue.svg)](https://spring.io/projects/spring-cloud)
[![JDK](https://img.shields.io/badge/JDK-21+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
   
</div>

---

## 📋 项目简介

Lambda Fusion 是一套面向企业的全栈微服务业务开发框架，基于 Spring Boot 4 / Spring Cloud 2025.1 / JDK 21 构建。在 [lambda-cloud-parent](https://gitee.com/westboy/lambda-cloud-parent) 基座之上，将权限、配置、数据字典、AI 智能平台等核心能力沉淀为开箱即用的 `lambda-fusion-*` starter，下游应用按需引入、聚焦业务，无需重复搭建基础设施。

### ✨ 核心特性

- 🔐 **权限体系** - RBAC + 轻量级多租户支持(字段级隔离)
- ⚙️ **动态配置** - 数据库配置源 + 热更新 + Nacos 发布
- 📚 **数据字典** - 静态 / 动态字典，枚举扫描注册
- 🤖 **AI 智能平台** - 智能应用、LLM 管理、知识库 RAG、子代理、MCP、技能市场、通道网关


## 🏗️ 项目架构

采用模块化设计，各模块职责清晰，可按需引入：

```
lambda-fusion-parent/
├── 📦 lambda-fusion-bom/                  # BOM 依赖管理
├── 🎯 lambda-fusion-core/                 # 核心模块（分页/CRUD/树/字典/身份）
├── 🔐 lambda-fusion-authority-api/        # 权限 API（Dubbo 远程认证 + Sa-Token 适配）
├── 🔐 lambda-fusion-authority/            # 权限管理（用户、角色、组织、资源、租户、客户端）
├── 🛡️ lambda-fusion-permission/           # 权限控制（聚合模块）
│   ├── lambda-fusion-permission-api/          # API 权限元数据（client/server）
│   └── lambda-fusion-permission-datascope/    # 数据权限授权树
├── ⚙️ lambda-fusion-config/               # 配置管理（dbconfig + 热更新 + Nacos 发布）
├── 📚 lambda-fusion-dictionary/           # 数据字典（静态/动态字典、枚举扫描）
├── 🗄️ lambda-fusion-datasource/           # 动态数据源管理（server/client、Dubbo 分发）
├── 📤 lambda-fusion-oss/                  # 附件管理（七牛/S3，按租户隔离）
├── 🤖 lambda-fusion-ai/                   # AI 模块（基于 AgentScope 2.0）
└── 🚀 lambda-fusion-startup/              # 可运行演示应用（端口 20005）
```


## 🎯 核心功能
| 项目 | 说明 | 
|------|---------|
| 👤 **用户管理** | 用户信息维护、在线状态监控、密码策略管理 |
| 🎭 **角色管理** | 角色权限分配、角色组管理、角色继承 |
| 🏢 **组织架构** | 多层级组织结构、部门管理、岗位管理 |
| 📋 **资源管理** | 菜单权限、按钮权限、API 资源控制 |
| 🔑 **客户端管理** | 客户端管理、授权、访问控制 |
| 🏠 **多租户支持** | 单一共享库 + tenant_id 字段级隔离，会话/业务按租户过滤 |
| 🔒 **认证服务** | 用户认证、SSO 支持、动态菜单生成 |
| 🤖 **智能应用** | CHAT/WORKSPACE 两型、自演化、多沙箱后端、SSE 流式对话 |
| 🤖 **知识库 RAG** | 文档切块入库、pgvector 向量库、三种检索注入模式（GENERIC/AGENTIC/BOTH） |
| 🤖 **子代理 / 技能 / MCP / 通道** | DB 驱动子代理、技能市场、MCP 工具接入、钉钉/飞书/企微通道适配 |


## 🛠️ 技术栈

| 技术 | 版本     | 说明 |
|------|--------|------|
| ☕ **Java** | 21+    | 最新 LTS 版本，性能优异 |
| 🍃 **Spring Boot** | 4.0.2  | 微服务开发框架 |
| ☁️ **Spring Cloud** | 2025.1.x | 微服务生态组件 |
| 🔐 **Sa-Token** | Latest | 轻量级权限认证框架 |
| 💾 **MyBatis Plus** | Latest | 增强版 ORM 框架 |
| 🔄 **MapStruct** | Latest |高性能对象映射 |
| 🤖 **AgentScope** | 2.0 | AI 多智能体运行时（harness/模型/沙箱/通道/技能/RAG） |
| 🌶️ **Lombok** |Latest | 简化 Java 代码 |
| ☁️ **Nacos** |Latest | 配置中心 / 注册中心 |
| ☁️ **Dubbo** |Latest | 远程认证 / 数据源 / 权限元数据同步 |
| ☕ **Caffeine** |Latest | 高性能本地缓存 |
| 🔧 **Hutool** |Latest | Java 工具类库 |


## 🎨 预览与体验

> 以下为 Lambda Fusion 系统主要功能界面预览，点击图片可查看高清原图。

<div align="center">
<table>
  <tr>
    <td align="center"><img src="docs/images/img_0.png" alt="预览图 1" width="420"/></td>
    <td align="center"><img src="docs/images/img_1.png" alt="预览图 2" width="420"/></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/img_2.png" alt="预览图 3" width="420"/></td>
    <td align="center"><img src="docs/images/img_3.png" alt="预览图 4" width="420"/></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/img_4.png" alt="预览图 5" width="420"/></td>
    <td align="center"><img src="docs/images/img_5.png" alt="预览图 6" width="420"/></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/img_6.png" alt="预览图 7" width="420"/></td>
    <td align="center"><img src="docs/images/img_7.png" alt="预览图 8" width="420"/></td>
  </tr>
</table>
</div>


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
            <groupId>com.lambda.cloud</groupId>
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
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-authority</artifactId>
</dependency>

<!-- 配置管理 -->
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-config</artifactId>
</dependency>

<!-- 数据字典 -->
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-dictionary</artifactId>
</dependency>

<!-- AI 智能平台（基于 AgentScope 2.0） -->
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-ai</artifactId>
</dependency>
```

> 基座 `lambda-cloud-parent`、`lambda-cloud-project-parent` 需先安装到本地仓库或私服，再构建本仓库。

### 构建与测试

本仓库没有 Maven wrapper，请使用系统 `mvn`（Maven 3.8+、JDK 21）。

```bash
# 构建 + 安装到本地仓库（在仓库根目录执行）
mvn clean install

# 仅构建单个模块及其 fusion 依赖（-am = 同时构建依赖）
mvn -pl lambda-fusion-authority -am clean install

# 仅编译（同时触发 Spotless 检查 + SpotBugs 检查，见「代码风格与静态检查」）
mvn -pl lambda-fusion-config compile

# 运行某模块的全部测试（目前仅 lambda-fusion-ai 有测试）
mvn -pl lambda-fusion-ai test

# 运行单个测试类 / 单个方法
mvn -pl lambda-fusion-ai test -Dtest=AgentGraphTest
mvn -pl lambda-fusion-ai test -Dtest=AgentGraphTest#shouldRoute
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
