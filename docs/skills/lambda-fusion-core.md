---
name: "lambda-fusion-core"
description: "面向基础设施（CRUD 抽象、分页、树形构建/过滤、身份与租户工具、字典映射注解等）的模块分析与改造指南。Invoke when 需求涉及通用能力或被多个业务模块复用的基础代码。"
---

# lambda-fusion-core 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造通用 CRUD、分页查询、排序安全、树形结构构建与过滤、通用实体基类等“跨模块基础能力”时。
- 需要统一身份/租户相关读取方式（AuthUtils、UserDetails 等）时。
- 需要为枚举提供字典映射（DictMapper + DictEnum）并被 dictionary/authority/datasource/ai 等模块复用时。

## 模块定位与边界

- 本模块不直接提供 Controller/数据库表，属于“基础库”。
- 其他模块会直接依赖它，因此改动需要兼容性优先：字段命名、泛型签名、工具方法行为一旦变化会波及多模块。

## 关键入口（从需求快速定位）

- 系统常量（角色等）：FusionConstants（lambda-fusion-core: com.lambda.fusion.core.FusionConstants）
- 字典映射注解：@DictMapper（com.lambda.fusion.core.annotation.DictMapper）
- 字典枚举协议：DictEnum（com.lambda.fusion.core.dict.DictEnum）
- 用户身份模型：UserDetails（com.lambda.fusion.core.identity.UserDetails）
- 通用 CRUD 抽象：AbstractCrudService（com.lambda.fusion.core.service.AbstractCrudService）
- 分页查询模型：Pagination（com.lambda.fusion.core.pagination.Pagination）
- 树结构体系：
  - TreeNode（com.lambda.fusion.core.tree.TreeNode）
  - TreeBuilder（com.lambda.fusion.core.tree.builder.TreeBuilder）
  - TreeDataFilter（com.lambda.fusion.core.tree.filter.TreeDataFilter）
- 安全/身份工具：AuthUtils（com.lambda.fusion.core.utils.AuthUtils）
- SQL 工具：SqlParamUtils（com.lambda.fusion.core.utils.SqlParamUtils）

## 设计约束（修改时必须守住的点）

- **向后兼容优先**：core 的 public API 被大量依赖，优先通过新增方法/重载而不是破坏性修改。
- **安全默认值**：分页排序、SQL 参数拼装必须保持防注入语义（不要回退到字符串拼接）。
- **树结构稳定性**：TreeBuilder/TreeDataFilter 属于基础算法，变更需关注性能与父子关系不丢失。

## 常见改造任务指引

- **新增通用能力**：优先放在 core，并通过最小依赖原则实现（避免引入大而重的外部库）。
- **需要字典映射的新枚举**：采用 `@DictMapper + DictEnum` 的模式，保持 code/label 字段语义一致，供 dictionary 模块扫描/输出。
- **身份/租户读取口径统一**：优先收敛到 AuthUtils，业务模块不要自行解析 ThreadLocal 或请求头。
