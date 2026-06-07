---
name: "lambda-fusion-dictionary"
description: "数据字典模块（字典类型/字典项、树形字典、动态字典、枚举扫描与注册）。当需求涉及统一字典管理或动态字典解析时调用。"
---

# Lambda Fusion Dictionary

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-dictionary</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/autoconfig/DictAutoConfiguration.java`
- 模块装配类：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/DictConfigure.java`
- 配置属性：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/DictProperties.java`
- AutoConfiguration imports：`lambda-fusion-dictionary/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Liquibase 变更脚本：`lambda-fusion-dictionary/src/main/resources/META-INF/db/changelogs/lambda-dict-changelog.xml`

## 配置
配置前缀是 `lambda.fusion.dict`。当前关键配置包括：
- `allowed-cascade-delete`
- `http-remote-host-prefix`
- `enable-dubbo-provider`

## 主要能力
- 字典类型与字典项管理
- 树形字典与动态字典查询
- SQL 字典、URL 字典、枚举字典解析
- 枚举字典扫描、注册与统一持有
- 用户字典与系统字典用途隔离

## 入口定位
- 配置属性：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/DictProperties.java`
- 字典类型控制器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/controller/DictTypeController.java`
- 字典项控制器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/controller/DictInfoController.java`
- 字典类型服务：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/service/impl/DictTypeServiceImpl.java`
- 字典项服务：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/service/impl/DictInfoServiceImpl.java`
- 注册器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/support/registry/DictRegistry.java`
- 注册后处理器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/support/registry/DictRegistryPostProcessor.java`
- 枚举扫描器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/support/scanner/DictEnumScanner.java`
- SQL 解析器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/support/resolver/SqlDictResolve.java`
- URL 解析器：`lambda-fusion-dictionary/src/main/java/com/lambda/fusion/dict/support/resolver/UrlDictResolve.java`

## 关键机制
- 动态字典解析通过 `DictSourceResolver` 扩展点完成，当前内置 SQL 和 URL 两类解析器
- 枚举字典通过 `@DictMapper` + `DictEnumScanner` + `DictRegistry` 注册到内存持有器
- `DictTypeController` 和 `DictInfoController` 同时覆盖树形字典、动态字典、启停、可选状态切换等能力
- 非开发者创建或更新字典时会受用途限制，系统字典与用户字典处理策略不同
- URL 字典支持带 token 调用本地或远端 HTTP 接口，SQL 字典使用 JSQLParser 做 `SELECT` 语句校验

## 条件装配说明
- `TreeDataFilter` 在未提供自定义 Bean 时使用默认实现
- `DictionaryDubboConfigure` 仅在 Dubbo 类路径存在时生效
- `enable-dubbo-provider` 目前在 `DictProperties` 中保留，但当前模块未看到直接基于该属性的 provider 暴露逻辑，修改时不要假设它已经接线完成

## 常见改造入口
1. 调整字典类型/字典项管理逻辑时，优先检查 `DictTypeController`、`DictInfoController`、对应 service 和 mapper。
2. 调整动态字典解析时，检查 `DictSourceResolver`、`SqlDictResolve`、`UrlDictResolve`、`UrlDictResolveExt`。
3. 调整枚举字典接入与注册流程时，检查 `DictRegistryPostProcessor`、`DictEnumScanner`、`DictRegistry`、`DictHolder`。
4. 调整系统字典/用户字典权限边界时，检查控制器和 service 中对 `DictUsage`、租户信息、当前登录人的处理逻辑。
