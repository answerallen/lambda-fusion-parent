---
name: "lambda-fusion-authority-api"
description: "Authority 客户端 API 模块（远程认证接口 + Dubbo 客户端自动装配 + Sa-Token 适配）。当其他服务需要复用统一角色/权限查询能力时调用。"
---

# Lambda Fusion Authority API

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-authority-api</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-authority-api/src/main/java/com/lambda/fusion/autoconfig/AuthorityClientAutoConfiguration.java`
- AutoConfiguration imports：`lambda-fusion-authority-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 配置
- 当前模块没有独立的 `@ConfigurationProperties` 前缀
- 远程引用能力依赖 Dubbo 环境
- Dubbo 注册中心、协议、分组版本等配置由上层 Dubbo 配置体系提供

## 主要能力
- 暴露统一的远程认证接口 `RemoteAuthenticationService`
- 在客户端服务中自动创建 Dubbo `ReferenceBean`
- 将远程认证接口适配成 Sa-Token `StpInterface`

## 入口定位
- 远程接口：`lambda-fusion-authority-api/src/main/java/com/lambda/fusion/authority/api/RemoteAuthenticationService.java`
- 自动配置：`lambda-fusion-authority-api/src/main/java/com/lambda/fusion/autoconfig/AuthorityClientAutoConfiguration.java`

## 关键机制
- `RemoteAuthenticationService` 直接继承 `StpInterface`
- 存在 Dubbo `ReferenceBean` 类时，自动配置会创建 `ReferenceBean<RemoteAuthenticationService>`
- 自动配置进一步将 `RemoteAuthenticationService` 暴露为 `StpInterface`，供 Sa-Token 使用
- 当前模块本身不实现角色/权限查询逻辑，只定义接口并负责客户端接入

## 条件与依赖说明
- Dubbo 客户端引用依赖 `org.apache.dubbo.config.spring.ReferenceBean` 在类路径中存在
- 模块依赖 authority 服务端实际提供 `RemoteAuthenticationService` 实现
- POM 中 `lambda-cloud-starter-dubbo` 和 `lambda-cloud-starter-nacos` 是可选依赖，最终是否生效取决于业务应用自身依赖和配置

## 常见改造入口
1. 调整远程认证接口能力时，优先检查 `RemoteAuthenticationService`，并同步服务端 `lambda-fusion-authority` 的实现。
2. 调整客户端自动装配逻辑时，检查 `AuthorityClientAutoConfiguration` 中的 `ReferenceBean` 和 `StpInterface` 暴露方式。
3. 排查客户端权限查询不生效时，优先确认 Dubbo 环境、服务注册发现和 authority 服务端是否已正确暴露接口。
