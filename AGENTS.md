# AGENTS.md

本文件是 AI 编码助手（Claude Code、Codex、Cursor 等）在本仓库中工作的**首要事实来源**。`CLAUDE.md` 通过导入方式引用本文件而非复制内容——请编辑本文件，不要编辑 CLAUDE.md。

## 项目简介

Lambda Fusion 是一套基于 `lambda-cloud-parent`（基础框架，位于本仓库*之外*的 `F:\developer\git\lamuda-cloud-parent`，在此通过 `_lambda_cloud_parent` 软链接引入）构建的企业级 Spring Boot **starter 模块**业务开发框架。本仓库产出一组供下游应用依赖的 `lambda-fusion-*` jar——它本身**不是**可运行应用，仅 `lambda-fusion-startup` 是演示用的可运行模块。

- Java 21、Spring Boot 4.0.2、Spring Cloud 2025.1.1、Spring Cloud Alibaba 2025.1.0.0
- 父 POM 为 `com.lambda.cloud:lambda-cloud-parent:2026.1.1-SNAPSHOT`；版本/依赖 BOM 为 `lambda-cloud-starter-dependencies`。两者都是 SNAPSHOT——见下方构建注意事项。
- 基础包名：各模块使用 `com.lambda.fusion.*`；启动演示模块使用 `com.fusion.startup`。
- 版本/分支约定：`2026.1.1-SNAPSHOT`，当前分支 `2026.1`。

## 构建与测试

本仓库没有 Maven wrapper——请使用系统 `mvn`（Maven 3.8+、JDK 21）。

```bash
# 构建 + 安装到本地仓库（在仓库根目录执行）
mvn clean install

# 仅构建单个模块及其 fusion 依赖（-am = 同时构建依赖）
mvn -pl lambda-fusion-authority -am clean install

# 仅编译（同时会触发 Spotless 检查 + SpotBugs 检查，见「代码风格与静态检查」）
mvn -pl lambda-fusion-config compile

# 运行某模块的全部测试（目前仅 lambda-fusion-ai 有测试）
mvn -pl lambda-fusion-ai test

# 运行单个测试类 / 单个方法
mvn -pl lambda-fusion-ai test -Dtest=AgentGraphTest
mvn -pl lambda-fusion-ai test -Dtest=AgentGraphTest#shouldRoute
```

**构建注意事项：** 由于父 POM 与 BOM 都是 `2026.1.1-SNAPSHOT`，必须先把基础框架安装到本地仓库——在 `F:\developer\git\lamuda-cloud-parent` 下执行 `mvn clean install -N`（或完整 install）后再构建本仓库，否则 SNAPSHOT 解析会失败。

测试运行在 classpath 上而非 module path（父 POM 设置了 `surefire.useModulePath=false`）——遇到 JPMS 相关的测试失败时请留意这一点。

## 运行启动演示模块

`lambda-fusion-startup` 把所有 fusion 模块组装成一个可运行的 Spring Boot 应用（`com.fusion.startup.FusionApplication`，端口 20005）。`application.yml` 中的运行时配置完全由环境变量驱动（`${MAIN_DB_URL}`、`${REDIS_HOST}`、`${QINIU_*}`、`${NACOS_*}` 等）。这些变量来自仓库根目录下本地、**已 gitignore** 的 `.evn` 文件（注意拼写——文件名就是 `.evn`）。不要提交真实值；可参考现有 `.evn` 了解所需变量名。

在 IDE 中运行 `FusionApplication`，或执行 `mvn -pl lambda-fusion-startup spring-boot:run`。产出的 jar **未**经过 repackage、不可直接执行，因此除非自行添加 `spring-boot-maven-plugin` 的 repackage goal，否则 `java -jar` 无法运行。

## 代码风格与静态检查

父 POM 在 **`compile`** 阶段强制执行两道关卡，因此 `mvn compile` 就会在违规时失败：

- **Spotless - Palantir Java Format**（`PALANTIR` 风格，v2.67.0，*不*格式化 Javadoc）。所有 Java 代码必须已符合 Palantir 规范，否则构建失败。自动修复：`mvn spotless:apply`；仅检查：`mvn spotless:check`。
- **SpotBugs**——在 compile 阶段运行 `check`，使用各模块的 `spotbugs-exclude.xml`。根目录设置 `spotbugs.skip=false`；`lambda-fusion-ai` 设置 `spotbugs.skip=true`。可按模块用 `-Dspotbugs.skip=true` 覆盖。

注解处理器由父 POM 装配：Lombok、MapStruct（+ `lombok-mapstruct-binding`）、`spring-boot-configuration-processor`，以及自定义的 `lambda-cloud-processor`。`lombok.config`（`addLombokGeneratedAnnotation=true`）由 antrun 步骤在每次构建时重新生成——不要手动编辑。

## 架构

### 模块分层

```
lambda-fusion-bom                 # 仅版本 BOM（packaging=pom，无代码）
lambda-fusion-core                # 基础库：Pagination、TreeBuilder、AbstractCrudService、
                                 #   identity/AuthUtils、@DictMapper / DictEnum。无自动配置。
lambda-fusion-authority-api       # 轻量：RemoteAuthenticationService（Dubbo）+ Sa-Token StpInterface 适配
lambda-fusion-authority           # RBAC/认证域：用户、角色、资源、组织、租户、客户端、区域、
                                 #   第三方登录、认证上下文。依赖 core、authority-api、
                                 #   permission-api、permission-datascope。
lambda-fusion-permission (pom)    # 聚合模块，包含下方两个子模块
  ├ permission-api                # API 权限元数据：本地 JSON 加载、注册表/匹配器、
  │                               #   client 推送 / server 接收、Dubbo 同步。mode=client|server
  └ permission-datascope          # 数据权限授权树 + 智能继承（事件驱动）
lambda-fusion-config              # dbconfig：ConfigData 加载器（数据库作为配置源）+ 配置管理
                                 #   API + 自动刷新 + 可选 Nacos 发布
lambda-fusion-dictionary          # 字典类型/字典项、树形与动态字典、枚举扫描注册
lambda-fusion-datasource          # server/client 动态数据源管理、Dubbo 分发
lambda-fusion-oss                 # 附件管理，基于 lambda-cloud-starter-oss（七牛/S3），按租户隔离
lambda-fusion-ai                  # RAG 知识库、Agent 工作流、聊天、LLM、MCP、提示词管理
lambda-fusion-startup             # 可运行演示应用，组装上述全部模块
```

fusion 模块之间的 `optional=true` 依赖（如 authority -> datasource/config/dubbo/nacos）是刻意为之：模块在可选依赖缺失时必须**优雅降级**。在未核对条件装配逻辑前，不要把可选依赖改成强依赖。

### 模块自动配置约定（需遵循的模式）

每个业务模块都是一个 Spring Boot 自动配置单元，遵循一致的「三件套」结构：

1. `com.lambda.fusion.autoconfig.XxxAutoConfiguration`——`@AutoConfiguration` 入口，注册在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中（每行一个类，文件末尾无空行）。
2. `com.lambda.fusion.<domain>.XxxConfigure`——`@Configuration` 模块装配类；条件 Bean 放在这里。
3. `com.lambda.fusion.<domain>.XxxProperties`——`@ConfigurationProperties(prefix = "lambda.fusion.<domain>")`。

配置前缀：`lambda.fusion.authorize`（authority）、`lambda.fusion.permission`、`lambda.fusion.datascope`、`lambda.fusion.config`、`lambda.fusion.dict`、`lambda.fusion.datasource`、`lambda.fusion.ai`。cloud-starter 的前缀（`lambda.oss`、`lambda.security`、`lambda.liquibase`、`lambda.sse`、`lambda.cache` 等）来自基础框架。

为模块新增功能时：优先通过 `@ConditionalOnClass` / `@ConditionalOnProperty` / `ObjectProvider` 扩展其 `Configure`，而不是添加无条件 Bean；并优先定义可供下游应用覆盖的扩展点接口（如 `ConfigChangeHandler`、`DataViewProvider`、`DictSourceResolver`、`TreeDataFilter`）。

### 横切关注点：租户隔离

租户隔离为**单一共享库 + 字段级隔离**模型：所有业务表通过 `tenant_id` 列区分租户，不再有按租户切换独立数据源（DEDICATED）的能力。

- authority 的 `TenantContextInterceptor` 解析并设置租户上下文（`tenant_id`）；
- 业务侧通过 `tenant_id` 字段过滤实现隔离，请求期不再按租户切换动态数据源。

动态数据源（`dynamic-datasource` + `p6spy`）仍用于多数据源/多库类型等通用场景，但与租户上下文解耦；演示中 `mybatis-plus.tenant.enabled` 默认为 `false`（租户隔离在应用层处理，而非 MP 的租户拦截器）。

### 数据库迁移（Liquibase）

`lambda-cloud-starter-liquibase` 会自动聚合迁移：扫描 classpath 中所有 `META-INF/db/changelogs/lambda-*-changelog.xml`（正则 `lambda-\w*-changelog.xml`），通过一个 master changelog 执行。**`lambda-datasource-changelog.xml` 被强制最先执行**，`lambda-additional-changelog.xml` 最后执行。

因此，新增/扩展模块表结构时：编辑该模块 `META-INF/db/changelogs/` 下的 `lambda-<module>-changelog.xml`。命名必须严格匹配该模式，否则不会被加载。新增表时，若该域已有对应的 `docs/sql/la_*.sql` 参考脚本，也应同步更新。

### 跨服务：Dubbo、Nacos、Sa-Token

- **Sa-Token** 是认证框架。`authority-api` 中的 `RemoteAuthenticationService` 继承 `StpInterface`；客户端自动配置为其暴露一个 Dubbo `ReferenceBean`，使其他服务可远程复用角色/权限查询。
- **Dubbo** 用于远程认证、远程数据源同步和权限元数据同步。所有 Dubbo `ServiceBean`/`ReferenceBean` 都通过 `@ConditionalOnClass` 判断 Dubbo 是否在 classpath 上才装配。
- **Nacos** 用作配置中心 + 注册中心。在多数模块中为可选（`@ConditionalOnClass` + `spring.cloud.nacos.config.enabled`）。
- **authority** 还条件性地暴露 Dubbo 远程认证（`AuthorityConfigure.DubboServiceConfiguration`）；`TenantManager` 仅在存在 `DataSourceManageService` 时才创建。

### AI 模块要点

`lambda-fusion-ai` 是最自包含的域：RAG（基于 `ai-postgres` 数据源的 pgvector，文档解析用 PDFBox/POI）、基于 **langgraph4j**（+ langchain4j）的 Agent 工作流、LLM 提供方（Ollama、OpenAI）、MCP 客户端、提示词管理、用 GraalVM JS 做条件求值、用 Resilience4j 做容错。它依赖 `lambda-fusion-core` + `lambda-fusion-datasource`。这是唯一带有完整测试套件的模块——改动 agent/graph/evaluator/node 逻辑时，执行 `mvn -pl lambda-fusion-ai test`。

## 提交规范（Conventional Commits）

本仓库使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格的提交信息，格式为 `<type>(<scope>): <描述>`：

- **type**：`feat`（新功能）、`fix`（缺陷修复）、`refactor`（重构，不含行为变更）、`style`（格式/命名统一）、`docs`（文档）、`chore`（构建/依赖等杂项）、`test`、`perf`。历史提交以 `refactor`、`feat` 为主。
- **scope**（可选）：受影响的模块或域，如 `authority`、`core`、`database`、`permission`、`dictionary`、`ai`，或完整模块名 `lambda-fusion-authority` 等。
- **描述**：中英文均可（仓库内两者混用），祈使语气、简明扼要。

示例（均取自实际历史提交）：

- `feat(authority): 添加第三方登录配置开关`
- `fix(database): 更新权限表字段长度配置`
- `refactor: Simplify Lombok imports and add empty method to PageView`
- `style(database): 统一数据库表名命名规范为小写`

仓库未配置 commit-msg 钩子或提交模板，以上为约定而非强制；请避免历史中出现过的不带描述的裸 `fix` / `refactor` 这类提交。

## OpenSpec 工作流（可选，规范驱动变更）

这是一套**可选的**规范驱动开发流程。`openspec` CLI 未随仓库分发，需开发者自行安装：`npm install -g @fission-ai/openspec`。**未安装该 CLI 时，`/opsx:*` 斜杠命令和 `openspec` 命令行调用不可用**；但 `openspec/changes/` 与 `openspec/specs/` 下的 markdown 仍可作为普通文档阅读，参考既有变更的设计思路与任务拆解。

若已安装 CLI，可由 `/opsx:*` 斜杠命令驱动：

- `/opsx:propose <name>`——创建 `openspec/changes/<name>/` 脚手架，生成 `proposal.md` + `design.md` + `tasks.md` + `specs/<capability>/spec.md`。
- `/opsx:apply [<name>]`——实现某个变更中的任务，将 `tasks.md` 里的 `- [ ]` 勾选为 `- [x]`。
- `/opsx:archive`——将已完成的变更归档。
- `/opsx:explore`、`/opsx:sync`——浏览与同步规范。

进行中的变更位于 `openspec/changes/`；已归档规范位于 `openspec/specs/`。是否采用此流程由开发者自行决定；不采用时按常规方式直接修改代码即可。

## 模块深度文档

每个模块在 `docs/skills/<module>/SKILL.md` 中有一份权威说明——包含自动配置入口、配置项、主要入口类、关键机制、条件装配说明，以及「常见改造入口」。修改模块前请先阅读对应的 SKILL.md；它会告诉你某类改动应具体触及哪些类。
