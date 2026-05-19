---
name: "lambda-fusion-config"
description: "面向数据库配置中心、Spring ConfigData 扩展、自动刷新、（可选）Nacos 发布等需求的模块分析与改造指南。Invoke when 需求涉及配置存储/加载/热刷新。"
---

# lambda-fusion-config 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造“数据库配置中心”：配置的增删改查、批量查询/更新、配置项模型与校验。
- 需要让 Spring Boot 在启动时从数据库加载配置（ConfigDataLocationResolver / ConfigDataLoader）或运行时热刷新配置时。
- 需要把配置同步到 Nacos（可选依赖存在且开启）时。

## 模块定位与边界

- 配置控制面：提供配置管理 API + 持久化模型 + 自动刷新调度。
- 配置数据面：通过 Spring Boot ConfigData 扩展，把数据库作为 `PropertySource` 注入 Environment。

## 自动装配与入口

- 自动装配入口：ConfigAutoConfiguration（lambda-fusion-config: com.lambda.fusion.autoconfig.ConfigAutoConfiguration）
- 主要装配类（扫描、自动刷新、Nacos 支持）：ConfigConfigure（com.lambda.fusion.config.ConfigConfigure）
- 关键配置项：ConfigProperties（com.lambda.fusion.config.ConfigProperties）
- 常量/枚举：ConfigConstants（com.lambda.fusion.config.ConfigConstants）

## Spring ConfigData 扩展（启动期如何加载 DB 配置）

- SPI 注册（Spring Boot 3.x）：
  - META-INF/spring/org.springframework.boot.context.config.ConfigDataLoader
  - META-INF/spring/org.springframework.boot.context.config.ConfigDataLocationResolver
- 相关实现代码集中在 `commons/datasource` 与 `commons/environment` 包下：
  - DatabaseBasedConfigDataLoader / DatabaseBasedConfigDataLocationResolver / DataBaseBasedPropertySource

## 自动刷新机制（运行期如何热更新）

- 由 ConfigConfigure 按 `lambda.fusion.config.auto-refresh.enabled` 条件装配：
  - DatabaseConfigWatcher：轮询检测配置变更（环境 + SQL）并产出变化信号
  - DatabaseContextRefresher：调度刷新动作并回调 ConfigChangeHandler
  - 入口见：ConfigConfigure（约 L26-L43）
- 默认提供空实现 `ConfigChangeHandler`，业务侧可覆盖 Bean 来接入自定义行为（例如刷新缓存、推送事件等）。

## Nacos 集成（可选）

- 当 `spring.cloud.nacos.config.enabled=true` 且相关类存在时，装配 NacosConfigPublisher：
  - 入口见：ConfigConfigure（约 L45-L56）

## 数据库与 Liquibase

- Changelog：lambda-config-changelog.xml（lambda-fusion-config/src/main/resources/META-INF/db/changelogs/）

## 常见改造任务指引

- **新增配置类型/组件化渲染**：同步扩展 ConfigConstants 中的类型枚举（如需字典映射）、更新配置项模型与前端协议，并补齐 options 表结构。
- **调整加载 SQL 或隔离维度**：优先通过 ConfigProperties.QueryConfig 的 SQL 字段收口（避免硬编码散落），并确认 application/public 的兼容语义。
- **提升刷新一致性**：保持 “检测变更 → 决策刷新 → 刷新后回调” 的单向链路，避免在回调中再触发刷新导致抖动。
