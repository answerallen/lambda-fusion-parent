# lambda-fusion-permission

## 目标

- Client 模式：读取编译期产出的 `META-INF/permissions/api-permissions.json`，执行接口权限校验，并将接口元数据推送到服务端。
- Server 模式：接收并存储各微服务推送的接口权限元数据，对管理端提供查询接口，支撑接口权限与菜单绑定。

## 关键输入来源

- 注解处理器：`_lambda_cloud_parent/lambda-cloud-processor` 里的 `PermissionProcessor` 在编译期生成 JSON。
- 安全扩展：`_lambda_cloud_parent/lambda-cloud-starter-security` 暴露 `SecureInterceptor`，可插入自定义校验逻辑。

## 运行模式

- `lambda.fusion.permission.mode=client`
  - 启动时加载本地 JSON。
  - 默认尝试推送到 `serverBaseUrl + reportPath`。
  - 在未存在其它 `SecureInterceptor` Bean 时，启用 JSON 权限校验拦截器。
- `lambda.fusion.permission.mode=server`
  - 暴露上报/查询接口：
    - `POST /permission/apis/report`
    - `GET /permission/apis/applications`
    - `GET /permission/apis/applications/{application}`
    - `GET /permission/apis/applications/{application}/apis`
    - `GET /permission/apis/reported`

## 配置项

```yaml
lambda:
  fusion:
    permission:
      enabled: true
      mode: client
      client:
        check-enabled: true
        push-enabled: true
        fail-fast: false
        resource-path: META-INF/permissions/api-permissions.json
        server-base-url: http://127.0.0.1:20005
        report-path: /permission/apis/report
        auth-token: your-token
      server:
        expose-api: true
        auth-token: your-token
```

## 服务端绑定建议表

```sql
CREATE TABLE `la_api_resources` (
  `RESOURCE_ID` varchar(32) NOT NULL COMMENT '资源编号',
  `PERMISSION_ID` varchar(64) NOT NULL COMMENT '接口权限ID',
  PRIMARY KEY (`RESOURCE_ID`,`PERMISSION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口资源关联表';
```
