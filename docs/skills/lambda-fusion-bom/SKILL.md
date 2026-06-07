---
name: "lambda-fusion-bom"
description: "Lambda Fusion 组件 BOM（统一版本管理）。当用户需要在多模块工程中统一引入 fusion 系列组件版本时调用。"
---

# Lambda Fusion BOM

## 模块引入
该模块为 Maven BOM（packaging=pom），用于统一管理 `lambda-fusion-*` 组件版本。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.lambda.cloud</groupId>
            <artifactId>lambda-fusion-bom</artifactId>
            <version>${lambda.fusion.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 受管组件
受管 artifact 以 [pom.xml](file:///h:/java-project/lambda-fusion-parent/lambda-fusion-bom/pom.xml#L13-L60) 为准，包含但不限于：
- lambda-fusion-core
- lambda-fusion-authority / lambda-fusion-authority-api
- lambda-fusion-config
- lambda-fusion-dictionary
- lambda-fusion-datasource
- lambda-fusion-permission-api / lambda-fusion-permission-datascope
- lambda-fusion-oss

## 使用方式
引入 BOM 后，在业务工程中引用 fusion 组件时无需显式写版本号：
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-core</artifactId>
</dependency>
```

## 最佳实践
1. 推荐在父工程或顶层聚合工程统一 import BOM，避免各子模块版本漂移。
2. 若业务工程同时依赖 `lambda-cloud-*` 与 `lambda-fusion-*`，建议统一由上层 BOM/parent 管控版本，避免依赖冲突。

