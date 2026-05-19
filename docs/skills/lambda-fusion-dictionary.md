---
name: "lambda-fusion-dictionary"
description: "面向字典类型/字典项管理、动态字典解析（SQL/URL/枚举）、字典扫描注册、树形字典与缓存等需求的模块分析与改造指南。Invoke when 需求涉及 DictMapper/DictEnum 或字典接口。"
---

# lambda-fusion-dictionary 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造字典类型与字典项的管理 API、树形字典展示、字典启停与可选择状态控制时。
- 需要实现/改造动态字典来源：SQL 字典、URL 字典、枚举字典（扫描 `@DictMapper`）时。
- 需要把业务枚举输出成字典供前端渲染（authority/config/datasource/ai 等模块已有大量 `@DictMapper` 枚举）时。

## 自动装配与入口

- 自动装配入口：DictAutoConfiguration（lambda-fusion-dictionary: com.lambda.fusion.autoconfig.DictAutoConfiguration）
- 模块配置类（MapperScan/ComponentScan/TreeDataFilter）：DictConfigure（com.lambda.fusion.dict.DictConfigure）
- 配置属性：`lambda.fusion.dict.*`：DictProperties（com.lambda.fusion.dict.DictProperties）
- 常量与字典数据类型枚举：DictConstants（com.lambda.fusion.dict.DictConstants）

## 关键子域与代码分布

- Controller：com.lambda.fusion.dict.controller（DictTypeController / DictInfoController）
- 动态解析器（插件式）：
  - 解析器接口：DictSourceResolver（com.lambda.fusion.dict.commons.resolver.DictSourceResolver）
  - SQL 字典：SqlDictResolve（com.lambda.fusion.dict.commons.resolver.SqlDictResolve）
  - URL 字典：UrlDictResolve（com.lambda.fusion.dict.commons.resolver.UrlDictResolve）
  - 扩展 URL 解析：UrlDictResolveExt（com.lambda.fusion.dict.commons.resolver.UrlDictResolveExt）
- 枚举字典扫描：
  - DictEnumScanner（com.lambda.fusion.dict.commons.scanner.DictEnumScanner）
- 注册中心：
  - DictRegistry（com.lambda.fusion.dict.commons.registry.DictRegistry）
  - DictHolder（com.lambda.fusion.dict.commons.registry.DictHolder）

## 数据库与 Liquibase

- Changelog：lambda-dict-changelog.xml（lambda-fusion-dictionary/src/main/resources/META-INF/db/changelogs/）
- 表结构历史/初始化参考（docs/sql）：docs/sql/la_dict_type.sql、docs/sql/la_dict_info.sql

## 常见改造任务指引

- **让一个业务枚举自动出现在字典里**：在枚举上增加 `@DictMapper` 并实现 `DictEnum<T>`（示例可参考 AuthorityConstants（com.lambda.fusion.authority.AuthorityConstants）或 AiConstants（com.lambda.fusion.ai.AiConstants））。
- **新增一种动态字典来源**：新增 `DictSourceResolver` 实现 → 在 DictFactory/DictRegistry 装配链路中注册 → 补充 DTO 与 Controller 出参约束。
- **调整树形字典过滤/排序**：复用 core 的 TreeDataFilter（DictConfigure 已提供默认实现），避免在 service 层重复写树算法。
