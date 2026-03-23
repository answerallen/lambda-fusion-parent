# Lambda Fusion Permission API

`lambda-fusion-permission-api` 是 Lambda Fusion 框架中的功能权限管理模块，提供了基于角色的 API 访问控制（RBAC）和多模块权限信息的集中上报与鉴权能力。

## 模块职责

本模块主要解决微服务或多模块单体架构下，API 权限如何定义、收集、上报和校验的问题：

1.  **客户端（Client）**：
    *   **权限定义**：支持通过 JSON 文件（通常位于 `classpath*:permissions/**/*.json`）静态声明各接口所需的权限标识。
    *   **权限上报**：微服务启动时，自动解析本地权限 JSON 文件，并通过 Dubbo RPC 接口将本地的 API 权限元数据推送至统一的权限中心（Server）。
    *   **接口鉴权**：提供 `PermissionSecureInterceptor` 拦截器，基于 Sa-Token 在请求到达 Controller 前，自动匹配请求的 HTTP Method 和 Path，拦截并校验当前登录用户是否具备对应的权限。

2.  **服务端（Server）**：
    *   提供 `PermissionSyncApi` 的 Dubbo RPC 接口实现，接收各个业务子系统上报的 API 权限点。
    *   维护全局的 `ApiPermissionRegistry`，可供前端拉取生成角色授权菜单或进行集中式鉴权控制。

## 核心配置 (`lambda.fusion.permission`)

```yaml
lambda:
  fusion:
    permission:
      # 客户端配置 (业务子模块)
      client:
        # 是否开启接口鉴权拦截
        check-enabled: true
        # 本地权限声明文件路径
        resource-path: "permissions/**/*.json"
        # 上报时的安全校验 Token
        auth-token: "your-secret-token"
        # 当请求的 API 在本地权限文件中未匹配到时，是否拒绝访问
        deny-unmatched: false
      
      # 服务端配置 (权限中心模块)
      server:
        # 接收上报时的安全校验 Token (需与 Client 保持一致)
        auth-token: "your-secret-token"
```

## 工作原理

1.  **启动上报**：系统启动时，`PermissionClientInitializer` 会触发 `LocalPermissionLoader` 读取本地的 JSON 权限配置，并使用 `PermissionPushClient` 将数据推送到权限中心。
2.  **请求拦截**：HTTP 请求进入时，`PermissionSecureInterceptor` 根据请求的 URL 和 Method，在本地缓存的权限注册表中查找对应的 `ApiPermissionMetadata`。
3.  **权限校验**：如果匹配到需要的权限标识（如 `T1000000067`），则调用 `SaToken` 的 `checkPermission` 进行权限验证。
