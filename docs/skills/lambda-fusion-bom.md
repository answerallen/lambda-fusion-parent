---
name: "lambda-fusion-bom"
description: "面向统一依赖版本管理（dependencyManagement/BOM 导入）与模块版本对齐的指南。Invoke when 需要在外部项目一键对齐 lambda-fusion 依赖版本。"
---

# lambda-fusion-bom 模块 Skill

## 适用范围（何时使用）

- 外部业务应用想一次性对齐 `lambda-fusion-*` 依赖版本，避免逐个指定版本号、避免版本漂移时。
- 需要排查依赖冲突/版本不一致（尤其是多模块聚合工程）时。

## 模块定位与边界

- 本模块是纯 `pom`（packaging=pom），只提供 `dependencyManagement`，不包含任何运行时代码。
- 版本来源于父工程的 `${project.parent.version}`，用于统一对齐同一套发布版本。

## 关键入口

- BOM 定义：lambda-fusion-bom/pom.xml
  - 管理的坐标包括：core/authority/authority-api/config/dictionary/datasource/permission-api/permission-datascope/oss。

## 常见使用方式（在业务项目）

- 在业务项目 `dependencyManagement` 中 import：
  - `com.lambda.cloud:lambda-fusion-bom:${revision}`（版本与平台发布版本对齐）
- 然后添加具体模块依赖时可省略 `<version>`。

## 改造任务指引

- 新增模块发布后需要纳入 BOM：只改 lambda-fusion-bom/pom.xml 的 dependencyManagement，保持版本使用 `${project.parent.version}`。
