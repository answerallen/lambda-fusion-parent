# Lambda Fusion Permission DataScope

`lambda-fusion-permission-datascope` 是 Lambda Fusion 框架中的**数据权限控制面**模块。它与底层的 `lambda-cloud-starter-mybatis`（数据面）协同工作，共同实现了细粒度的行级数据安全隔离。

## 模块职责

本模块负责数据权限的**定义、分配和生命周期自动维护**：

1.  **统一的权限分配 API**：提供标准的 REST 接口（`/datascope/tree` 和 `/datascope/grant`），供前端统一渲染并提交任何业务类型（如部门、项目、客户等）的权限树。
2.  **策略模式（View Provider）**：引入 `DataViewProvider` 接口，解耦业务模型与权限树的构建。业务模块只需实现该接口，即可将自身的数据结构转换为标准的 `DataScopeNode`，无需创建复杂的数据库物理视图。
3.  **Smart 自动同步机制**：监听业务对象（如组织架构）的生命周期事件（创建、更新、删除、移动），自动修复并同步底层权限表 `la_datascopes`，防止因业务数据变更导致权限孤儿或越权。

## 核心架构设计

### 泛化授权主体 (Target Type)
打破传统的“只能给角色授权”的限制，本模块支持向**任何类型的主体**进行数据授权：
*   `USER`: 直接授权给特定用户
*   `ROLE`: 授权给系统角色
*   `ORG`: 授权给整个组织机构
*   `GROUP`: 授权给临时项目组或用户组
*   `CLIENT`: 授权给第三方 API 客户端

### 资源维度隔离 (Domain Type)
通过 `domain_type` 字段隔离不同的业务域：
*   `0`: 组织架构 / 部门数据
*   `1`: 项目管理数据
*   `2`: CRM 客户数据
*   ...支持无限扩展

## 核心配置 (`lambda.fusion.datascope`)

```yaml
lambda:
  fusion:
    datascope:
      # 是否开启 Smart 自动同步机制
      smart:
        enabled: true
        # 配置哪些业务实体的事件触发哪些 domain_type 的同步
        types:
          "com.lambda.fusion.authority.model.organization.Organization": [0]
```

## 快速接入

**1. 业务侧实现视图提供者 (以部门树为例)**
```java
@Component
public class OrgDataViewProvider implements DataViewProvider {
    @Override
    public boolean supports(int type) {
        return type == 0; // 处理 domain_type = 0 的请求
    }

    @Override
    public List<DataScopeNode> loadDataView(String tenantId) {
        // 查询数据库并组装为 DataScopeNode 列表
        return nodes;
    }
}
```

**2. 业务侧发布变更事件 (触发 Smart 机制)**
```java
// 在部门移动完成后
applicationEventPublisher.publishEvent(
    DataScopeObjectChangedEvent.moved(
        Organization.class.getName(), 
        orgId, newParentId, oldParentId, cascadeChildIds, operator
    )
);
```

## 注意事项
*   超级管理员（内置 `ROLE_ADMIN` 等标识）。
