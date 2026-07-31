# 标准包分层结构

> 本文以 `lambda-fusion-authority` 为参考标准，是 [engineering-contract.md](engineering-contract.md) §10 的配套细则。
> 冲突时以工程契约 §10 为准；本文负责把「长什么样」说具体。

## 1. 总体原则

业务模块采用「模块根级共享 + 子域内分层」结构：

- **模块根级**只放真正跨子域共享的内容：`exception/`、`{Domain}Constants`、（按需）`utils/`。
- **子域包**承载该子域全部代码，横切能力（adapter / assembler / datascope / interceptor / listener / provider）归属子域，放在对应子域包下，不得集中到 `commons/`（见工程契约 §10.3）。
- 新增代码必须补充到现有子域目录，禁止新建并行的旧式顶层分层目录（`vo/`、`dto/`、`entity/` 集中包）。
- 自动配置入口统一放在 `com.lambda.fusion.autoconfig`，不放在业务域包内（见工程契约 §3.4）。

## 2. 标准目录树

```
com.lambda.fusion.<domain>
├── exception/          模块级异常（跨子域共享，如 {Domain}BusinessException、错误码枚举）
├── {Domain}Constants   模块级常量（跨子域共享）
├── utils/              模块级工具（按需，跨子域共享）
└── <subdomain>/        子域包（authority 示例：area / authentication / client / organization /
                       #               resource / role / tenant / user）
    ├── adapter/        子域内外部接口适配（按需）
    ├── assembler/      子域内数据组装（按需）
    ├── controller/     子域内控制器（只编排 Service）
    ├── datascope/      子域内数据权限（DataViewProvider 实现，按需）
    ├── interceptor/    子域内拦截器（按需）
    ├── listener/       子域内事件监听（按需）
    ├── mapper/         子域内 Mapper
    ├── model/          子域内模型（Entity / VO / Query / Command，禁止业务逻辑）
    ├── provider/       子域内第三方登录等 provider（按需）
    └── service/impl/   子域内 Service 接口与实现

com.lambda.fusion.autoconfig
├── XxxAutoConfiguration   @AutoConfiguration 入口（注册到 AutoConfiguration.imports）
└── （由各域 *Configure / *Properties 分别位于 com.lambda.fusion.<domain>）
```

> `adapter/`、`assembler/`、`datascope/`、`interceptor/`、`listener/`、`provider/`、`utils/` 均为按需创建——当前子域/模块没有相应职责时不建空目录，有了再补。

## 3. 子包职责

| 子包 | 职责 | 禁止 |
| --- | --- | --- |
| `controller/` | HTTP 入口，只编排 Service 接口调用 | 写业务逻辑、直接调 Mapper |
| `service/` `impl/` | 业务能力实现，`impl/` 放 `*ServiceImpl` | 暴露实现层内部方法给 Controller |
| `mapper/` | 持久化接口，继承 `BaseMapper` / `LambdaBaseMapper` | 写业务逻辑 |
| `model/` | Entity / VO / Query / Command 定义 | 放业务逻辑 |
| `adapter/` | 子域内外部接口适配 | 与子域无关的通用适配 |
| `assembler/` | 多源 / 复杂组装（`ConverterResolver` 覆盖不了时） | 与 `ConverterResolver` 重复 |
| `datascope/` | `{Subdomain}DataViewProvider` 实现 | 自建权限关联表 |
| `interceptor/` `listener/` | 子域内拦截 / 事件监听 | 跨子域共享逻辑放这里 |
| `provider/` | 第三方登录等 provider 实现（如 alipay/dingtalk/wechat） | 与子域无关的 provider |

## 4. 命名

见工程契约 §10.2。重点：持久化实体 `{Name}Entity`、视图对象 `{Name}`（无后缀）、Mapper `{Name}Mapper`、Service `{Name}Service` / `{Name}ServiceImpl`、Controller `{Name}Controller`、自动配置 `*AutoConfiguration` / `*Configure` / `*Properties`。

## 5. 参考实现

`lambda-fusion-authority` 为参考标准，已落地的子域：`area` / `authentication` / `client` / `organization` / `resource` / `role` / `tenant` / `user`。其中：

- `authentication` 子域含 `adapter/`、`assembler/`、`provider/{alipay,dingtalk,wechat}`；
- `organization` 子域含 `datascope/`；
- `tenant` 子域含 `interceptor/`（`TenantContextInterceptor`）；
- `user` 子域含 `assembler/`、`listener/`。

`lambda-fusion-ai` 为自包含域参考（`apps` / `channel` / `chat` / `llm` / `mcp` / `rag` / `runtime` / `security` / `skill` / `subagent`），其 `runtime/workspace` 等子域内部自带 `entity/mapper/service/impl`，遵循同一分层约定。新增子域应遵循同一结构。

## 6. 特殊模块

- `lambda-fusion-core`：纯基础库（无自动配置、无 `@ConfigurationProperties`），包结构为 `annotation` / `convert` / `dict` / `entity` / `identity` / `pagination` / `service` / `tree/{builder,filter,model,util}` / `utils`，不套用业务子域分层。
- `lambda-fusion-bom`：仅版本 BOM（`packaging=pom`，无代码）。
- `lambda-fusion-authority-api` / `lambda-fusion-permission-api`：轻量 API 模块，仅放远程接口契约与适配，不套用完整业务分层。
- `lambda-fusion-startup`：启动演示模块，包名 `com.fusion.startup`，仅 `FusionApplication` 与静态资源。
