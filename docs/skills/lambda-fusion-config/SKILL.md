---
name: "lambda-fusion-config"
description: "数据库配置中心模块（dbconfig ConfigData 扩展、配置管理 API、自动刷新、可选 Nacos 发布）。当需求涉及运行期配置加载、刷新或配置管理时调用。"
---

# Lambda Fusion Config

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-config</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-config/src/main/java/com/lambda/fusion/autoconfig/ConfigAutoConfiguration.java`
- 模块装配类：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/ConfigConfigure.java`
- AutoConfiguration imports：`lambda-fusion-config/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- ConfigData SPI 注册：
  - `lambda-fusion-config/src/main/resources/META-INF/spring/org.springframework.boot.context.config.ConfigDataLocationResolver`
  - `lambda-fusion-config/src/main/resources/META-INF/spring/org.springframework.boot.context.config.ConfigDataLoader`
- Liquibase 变更脚本：`lambda-fusion-config/src/main/resources/META-INF/db/changelogs/lambda-config-changelog.xml`

## 配置
配置前缀是 `lambda.fusion.config`。当前关键配置包括：
- `application.home-path`
- `auto-refresh.enabled`
- `auto-refresh.initial-delay-seconds`
- `auto-refresh.interval-seconds`
- `auto-refresh.core-pool-size`
- `datasource.*`
- `query-config.select-configs-sql`
- `query-config.check-configs-changed-sql`

## 主要能力
- `dbconfig:` 配置导入：基于 Spring Boot ConfigData 从数据库加载配置
- 配置管理 API：配置项、选项、批量更新、手动刷新
- 设置布局与运行时设置管理
- 网关动态路由配置读写
- 数据库配置自动刷新
- 可选 Nacos 配置发布支持

## 入口定位
- 配置属性：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/ConfigProperties.java`
- REST 控制器：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/controller/ConfigController.java`
- 配置服务：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/service/impl/ConfigServiceImpl.java`
- ConfigData 解析器：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/datasource/DatabaseBasedConfigDataLocationResolver.java`
- ConfigData 加载器：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/datasource/DatabaseBasedConfigDataLoader.java`
- 自动刷新：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/refresh/DatabaseConfigWatcher.java`
- 上下文刷新：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/refresh/DatabaseContextRefresher.java`
- Nacos 发布：`lambda-fusion-config/src/main/java/com/lambda/fusion/config/nacos/NacosConfigPublisher.java`

## 关键机制
- `dbconfig:` 读取 `lambda.fusion.config.datasource.*`，并结合 `spring.application.name` 加载应用级和 `public` 公共配置
- 占位符 `${ENV_KEY:default}` 会在解析数据源配置时先展开
- `DatabaseContextRefresher` 负责监听数据库变更并刷新环境，也支持手动触发刷新
- `ConfigController` 不只是 CRUD，还覆盖网关动态路由、设置布局、运行时设置和选项更新

## 条件装配说明
- 自动刷新依赖 `lambda.fusion.config.auto-refresh.enabled`，默认开启
- `NacosConfigPublisher` 仅在 Nacos 相关类存在且 `spring.cloud.nacos.config.enabled` 未关闭时装配
- `ConfigChangeHandler` 默认是空实现，业务方可以自定义 Bean 覆盖

## 常见改造入口
1. 调整数据库加载逻辑时，优先检查 `config.datasource` 包下的 resolver、loader、resource 和 property source。
2. 调整刷新频率、线程模型或刷新触发时，检查 `ConfigConfigure`、`DatabaseConfigWatcher`、`DatabaseContextRefresher`。
3. 扩展配置管理接口时，优先检查 `ConfigController`、`ConfigServiceImpl`、`ConfigMapper`、`ConfigOptionMapper`。
4. 涉及网关动态路由或设置中心时，检查 `ConfigController` 中 `/gateway/routes`、`/settings/layout`、`/settings/runtime` 相关逻辑。
