---
name: "lambda-fusion-core"
description: "Fusion 基础能力库（分页、树结构、通用 CRUD 抽象、身份模型与工具、字典注解）。当用户需要复用基础模型与通用能力时调用。"
---

# Lambda Fusion Core

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-core</artifactId>
</dependency>
```

## 配置
该模块为基础库，不提供独立的自动配置入口与 `@ConfigurationProperties` 前缀。

## 使用方式
### 通用 CRUD 抽象
参考: [AbstractCrudService](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/service/AbstractCrudService.java)

### 树形数据构建
参考: [TreeBuilder](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/tree/builder/TreeBuilder.java)、[TreeNodeUtils](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/tree/util/TreeNodeUtils.java)

### 统一分页
参考: [Pagination](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/pagination/Pagination.java)

### 身份与鉴权工具
参考: [UserDetails](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/identity/UserDetails.java)、[AuthUtils](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/utils/AuthUtils.java)

## 机制说明
- 字典注解与枚举映射基础: [DictMapper](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/annotation/DictMapper.java)、[DictEnum](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-core/src/main/java/com/lambda/fusion/core/dict/DictEnum.java)

## 最佳实践
1. 业务模块优先依赖 `lambda-fusion-core` 统一分页、树结构与通用服务抽象，避免重复造轮子。
2. 若业务需要字典/权限/数据源等能力，建议搭配 `lambda-fusion-dictionary`、`lambda-fusion-permission-*`、`lambda-fusion-datasource` 等模块。

