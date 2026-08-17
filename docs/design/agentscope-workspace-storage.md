# AgentScope Workspace 存储设计

## 1. 设计目标

AgentScope Workspace 属于部署级基础设施，不属于应用自身配置。一个服务实例中的全部应用统一使用
`lambda.fusion.ai.workspace.storage.type` 指定的存储方式：

- `LOCAL`：沿用节点本地目录，适用于单节点部署。
- `MYSQL`：使用 AgentScope MySQL 分布式存储扩展，适用于多节点部署。
- `POSTGRES`：使用 AgentScope PostgreSQL 分布式存储扩展，适用于多节点部署。

本配置只管理 AgentScope Workspace。RAG 原始文档、向量库和 Agent state store 仍由各自配置管理。

## 2. 配置

```yaml
lambda:
  fusion:
    ai:
      workspace:
        root: ${AI_WORKSPACE_ROOT:./data/ai-workspaces}
        storage:
          type: ${AI_WORKSPACE_STORAGE_TYPE:LOCAL}
          mysql:
            datasource: ${AI_WORKSPACE_MYSQL_DATASOURCE:master}
          postgres:
            datasource: ${AI_WORKSPACE_POSTGRES_DATASOURCE:ai-postgres}
```

`lambda-fusion-ai` 的通用默认值为 `LOCAL`，保证既有单节点项目无配置升级。启动示例模块默认选择
`MYSQL`，与其既有 MySQL Agent state store 配置保持一致。

远程 Workspace 必须同时使用分布式 Agent state store。若 Workspace 为 `MYSQL`/`POSTGRES`，而
`state-store.type` 仍为 `MEMORY` 或 `FILE`，系统启动时直接失败，避免集群节点间状态与文件行为不一致。

AgentScope 分布式扩展会在首次初始化时创建 `agentscope_store`；启用沙箱快照时还会使用
`agentscope_snapshots`。目标数据库必须预先存在，连接账号需要具备相应建表和读写权限。启动示例已直接
引入 MySQL 扩展；使用 PostgreSQL 的部署还必须在最终启动模块中引入 `agentscope-extensions-postgresql`。

## 3. 存储与隔离

本地模式保持原路径：

```text
{workspace.root}/tenants/{tenantId}/apps/{appId}
```

远程模式以稳定的 `MD5("app:" + appId + ":t:" + tenantId)` Agent ID 建立命名空间，租户与应用不会共享 Workspace。
HOST 模式下，以下 AgentScope 管理内容进入分布式 BaseStore：

- `AGENTS.md`、`MEMORY.md`、`tools.json`
- `memory/`、`skills/`、`subagents/`、`knowledge/`、`plans/`
- `agents/{agentId}/sessions/`、`agents/{agentId}/tasks/`
- AgentScope 内部消息总线 `.agentscope/` 和 Lambda Fusion 审计目录 `.audit/`

沙箱模式继续使用 AgentScope 原生沙箱文件系统，运行数据通过相同 `DistributedStore` 的快照能力持久化，
不改成 HOST 的文件路由。节点本地 `.index/` 仅用于可重建索引，不进入共享存储，也不对管理 API 暴露。

初始化模板保存在当前节点：

```text
{workspace.root}/.remote-templates/{storageType}/tenants/{tenantId}/apps/{appId}
```

模板只是远程文件不存在时的只读初始内容，不作为运行期共享数据来源。远程文件系统使用 AgentScope 的
`RemoteFilesystem`、`OverlayFilesystem` 和 `CompositeFilesystem` 组合，并把所有本地回退层限制在应用模板目录，
防止未路由路径访问宿主机文件系统。

## 4. 并发模型

- HOST Workspace：一次 Agent 流式执行期间持有该 Agent ID 对应的 AgentScope 分布式执行锁。
- 沙箱 Workspace：沿用 AgentScope `SandboxManager` 的 acquire/release 锁窗口，避免重复加锁。
- 管理端修改 Workspace 文件时使用同一把锁，避免与 Agent 的记忆、自演化写入互相覆盖。
- 审计快照在 Agent 结束事件的同一锁窗口内写入，直接复用已持有的锁，避免非重入分布式锁自锁。

锁的粒度是租户应用对应的稳定 Agent ID，因此不同应用可并发执行，同一应用的 Workspace 写入串行化。

## 5. 切换与运维约束

存储类型由配置文件决定，不提供应用级选择，也不提供运行期修改接口。多节点必须使用完全相同的配置，
变更时应停止全部节点后统一修改并完整重启。

切换存储类型采用“新存储即新 Workspace”语义：

- 不迁移旧数据；
- 不在新旧存储之间同步；
- 不删除旧存储；
- 回切原配置时可重新看到原存储中的内容。

因此，切换前应由运维人员确认接受一个全新的 Workspace。应用删除也不扫描或清理其他存储类型中的历史
Workspace；远程存储的数据保留策略由数据库运维负责。
