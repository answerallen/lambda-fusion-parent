---
name: "lambda-fusion-authority"
description: "统一认证与权限域模块（认证上下文、第三方登录、用户/角色/资源/组织/租户/客户端/区域）。当需求涉及 authority 域分析、排障或改造时调用。"
---

# Lambda Fusion Authority

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-authority</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-authority/src/main/java/com/lambda/fusion/autoconfig/AuthorityAutoConfiguration.java`
- AutoConfiguration imports：`lambda-fusion-authority/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 模块装配类：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/AuthorityConfigure.java`
- 配置属性：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/AuthorityProperties.java`

## 配置
配置前缀是 `lambda.fusion.authorize`。当前关键配置包括：
- `use-org-name-as-id`
- `third-party-auto-register`
- `password-strategy.*`
- `third-part.wx-ma.*`
- `third-part.wx-open.*`
- `third-part.alipay-ma.*`
- `third-part.ding-talk.*`

## 主要能力
- 认证上下文输出：当前登录用户信息、菜单路由、权限集合
- 第三方登录接入：支付宝、钉钉、微信小程序、微信开放平台
- RBAC 与主数据管理：用户、角色、资源、组织、租户、客户端、区域
- 租户上下文注入
- Dubbo 远程认证能力暴露

## 入口定位
- 认证控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/authentication/controller/AuthenticationController.java`
- 认证服务：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/authentication/service/impl/AuthenticationServiceImpl.java`
- 第三方登录实现：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/authentication/provider/*`
- 用户控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/user/controller/UserController.java`
- 角色控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/role/controller/RoleController.java`
- 资源控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/resource/controller/ResourceController.java`
- 组织控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/organization/controller/OrganizationController.java`
- 租户控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/tenant/controller/TenantController.java`
- 客户端控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/client/controller/ClientController.java`
- 区域控制器：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/area/controller/AreaController.java`

## 关键机制
- 租户上下文拦截：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/tenant/interceptor/TenantContextInterceptor.java`
- 远程认证适配：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/authentication/adapter/RemoteAuthenticationServiceAdapter.java`
- 在线状态监听：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/user/listener/UserOnlineLogListener.java`
- SSE 访问监听：`lambda-fusion-authority/src/main/java/com/lambda/fusion/authority/user/listener/UserSeeEventListener.java`
- 数据库变更脚本：`lambda-fusion-authority/src/main/resources/META-INF/db/changelogs/`

## 条件装配说明
- Dubbo 远程认证暴露依赖 Dubbo 类路径条件，见 `AuthorityConfigure.DubboServiceConfiguration`
- `TenantManager` 仅在存在 `DataSourceManageService` 时创建
- Sa-Token 在线监听与 SSE 监听都依赖对应类存在时才装配

## 常见改造入口
1. 调整登录态、菜单、权限聚合时，优先检查 `AuthenticationServiceImpl`、`AuthenticationMapper`、`MenuRouteAssembler`。
2. 新增或修改第三方登录渠道时，检查 `AuthorityProperties.ThirdPartConfig` 与 `authentication.provider.*` 下的 handler/adapter。
3. 调整租户识别或租户初始化时，优先检查 `TenantContextInterceptor`、`TenantServiceImpl`、`TenantManager`。
4. 扩展用户、角色、资源、组织等字段时，同步修改 model、mapper、service、controller 与 Liquibase changelog。
