# AI 应用发布与独立聊天页设计

> 目标：为 `lambda-fusion-ai` 的智能应用增加可发布、可下线、可复制访问链接的独立入口；访问者可以匿名查看应用公开信息，但必须在发布页内完成登录后才能创建会话和对话。发布页采用类似 ChatGPT 的信息架构，但所有交互组件、设计变量和聊天能力继续基于 `tdesign-vue-next` 与 `@tdesign-vue-next/chat`。

## 1. 结论先行

发布后的页面应当与后台登录页面解耦，但不应另建一套认证系统：

- **页面入口独立**：发布 URL 不进入后台 `BasicLayout`，不展示后台菜单、面包屑、页签，也不跳转 `/auth/login`。
- **登录交互独立**：发布页使用自己的 TDesign 登录弹窗、验证码流程、登录态恢复和 401 处理。
- **身份事实复用**：仍调用现有 `/auth/login`、`/auth/jcaptcha`、`/auth/userinfo`，仍使用现有 Sa-Token、用户、角色与租户体系。
- **对话能力复用**：继续调用现有 Session、Message、Run、附件、SSE、HITL 接口，不创建“公开聊天”第二套运行时。
- **未登录不可对话**：匿名接口只返回经过白名单裁剪的应用公开资料；所有会话和对话接口继续要求有效登录态。
- **设计体系不分叉**：基础控件只用 `tdesign-vue-next`，消息、输入、附件、推理、工具调用只用当前已经接入的 `@tdesign-vue-next/chat@0.6.0` 能力。

## 2. 当前代码事实

### 2.1 后端应用模型

当前 `apps` 子域已经提供：

- `AppsController`：`ROLE_DEV` 管理应用的分页、详情、新增、更新、删除。
- `AppAvailabilityController`：登录用户查询自己可使用的应用。
- `AppServiceImpl`：校验模型、应用类型、RAG 模式、名称唯一性，并发布 `ConfigChangedEvent`。
- `AppEntity`：保存模型、提示词、工具、MCP、知识库、技能、子代理、应用类型等运行配置。
- `enabled`：应用是否可运行。
- `audience`：`B | C | ALL`；`B/C` 分别匹配 `AiProperties.audience.bRoles/cRoles`，`ALL` 对所有已登录用户可见。
- `ownerId`：非空时仅所有者可见；当前创建流程尚未写入该字段。

当前所谓“发布设置”只有 `audience + enabled`。它解决的是运行开关和登录用户可见性，尚不具备以下发布能力：

- 没有发布/下线生命周期；
- 没有稳定、不可枚举的访问标识；
- 没有匿名可读取的安全公开资料；
- 没有脱离后台布局的路由；
- 没有发布页专用登录交互；
- 没有链接复制、访问和发布状态展示。

### 2.2 后端对话模型可以直接复用

现有对话链路已经具备发布页所需的核心能力：

- `ChatSessionServiceImpl.create` 调用 `AppService.loadAvailable`，创建会话前会再次校验应用启用状态和当前用户可见性。
- 会话按 `tenant_id + user_id` 隔离，`loadOwned` / `selectOwned` 保证只能访问自己的会话。
- `ChatRunServiceImpl.createOrLoad` 在每次新回合开始前再次校验应用可用性。
- `ChatRun` 已支持后台续跑、断线恢复、停止、幂等发送和 HITL 工具确认。
- 附件接口已经校验会话所有权；预览链接采用签名 URL。
- 前端 `chat-panel.vue` 已经完成 AG-UI、文本流、推理、工具调用、附件、恢复和确认交互。

因此，发布功能不应新增 `PublicChatSession`、`PublicChatController`、匿名访客 ID 或第二套 Run 状态机。

### 2.3 前端现状

当前后台聊天页由两层组成：

- `chat/index.vue`：应用选择、会话列表、新建/删除会话。
- `chat/chat-panel.vue`：消息历史、AG-UI 流、附件、工具确认、输入区。

当前页面仍是后台页面：

- 路由 `/ai/chat` 属于有权限路由；
- 外层使用 `Page` 和后台 `BasicLayout`；
- 左侧允许切换多个应用；
- 登录失败或 Token 失效会进入全局后台重认证逻辑；
- `useAuthStore.authLogin` 登录成功后还会加载权限码、字典、后台菜单相关用户信息并连接全局 SSE。

这些后台编排不适合发布页，但底层登录 API、Token Store 和聊天面板可以复用。

认证与租户链路曾有一段为「登录前建立发布租户上下文」铺垫的分析（登录由 Filter 处理、MVC 拦截器无法覆盖 `/auth/login` 等）。该分析的结论——「发布页登录必须在 Servlet Filter 链解析租户」——已被推翻：发布功能**不需要**在登录前建立任何租户上下文（见 §6.4），登录完全复用现有流程、租户来自实际查到的用户。匿名 profile 是唯一跨租户查询，用单行精确查询解决，不进入 Filter 链。

### 2.4 当前需要同步修正的安全问题

`GET /v1/ai/apps/available` 当前直接返回 `AppEntity`。这会把 `systemPrompt`、`modelId`、工具白名单、MCP ID、知识库 ID、技能等内部配置返回给普通登录用户。

发布功能扩大了应用入口范围，实施时必须同时引入安全视图 `AvailableApp`，让普通聊天页和发布页只获得：

```text
id, name, avatar, description, appType, supportsVision
```

管理端的 `ROLE_DEV` 详情接口仍可返回完整管理模型。匿名公开资料中不得包含内部应用 ID 和任何运行配置。

另外，当前 `audience` 没有严格校验；除 `B` 外的未知值会落入 `C` 分支。发布前必须把 `B | C | ALL` 收敛为明确枚举并在创建、更新时校验。

## 3. 范围与非目标

### 3.1 本期范围

- 一个应用最多有一个发布入口。
- 首次发布生成稳定链接。
- 支持发布、下线、重新发布、复制链接、打开链接。
- 匿名用户可看应用名称、头像和描述。
- 登录后按现有 `audience / ownerId / enabled` 规则授权。
- 发布页支持会话列表、新建、删除、历史回放、流式对话、附件、停止、恢复和 HITL。
- 桌面端为固定侧栏，移动端用 TDesign Drawer 承载会话历史。

### 3.2 明确不做

- 不允许匿名对话。
- 不新增访客账号、短信快捷注册、第三方登录或邀请码体系。
- 不复制应用配置形成“发布版本快照”。
- 不新增发布版本表、灰度发布、多个环境或多个发布渠道。
- 不为未来多域名、独立部署或自定义主题预埋配置开关。
- 不复制现有 Session、Run、附件和 AG-UI 实现。
- 不照搬 ChatGPT 品牌、图标或像素；只采用其成熟的布局层级和内容密度。

## 4. 三个状态必须分开

应用的运行、授权和发布是三个不同事实：

| 事实 | 字段/规则 | 含义 |
| :--- | :--- | :--- |
| 运行开关 | `enabled` | 是否允许应用创建新会话/新回合，是全局硬开关 |
| 登录用户授权 | `audience + ownerId` | 哪些已登录用户可使用应用 |
| 外部入口 | `publishStatus` | 独立发布 URL 是否可被解析和展示 |
| 运行配置 | `ai_app` 当前行 | 提示词/模型/工具等唯一事实来源，**编辑即生效、不做版本快照** |

有效发布访问必须同时满足：

```text
publishStatus == PUBLISHED
AND enabled == true
AND 当前登录用户通过 audience / ownerId 校验
```

行为约定：

- **下线**只关闭独立 URL，不删除应用、不关闭后台调试入口、不删除历史会话。
- **停用**是运行硬开关；发布页和后台聊天都不能再开启新会话或新回合。
- **重新发布**恢复原 URL，避免已经分发的链接失效。
- **保存应用配置**继续遵循现状：新会话/后续运行读取最新已保存配置，不生成发布快照。
- 纯发布状态变化不影响 Agent 配置，不应发送 `ConfigChangedEvent`。

## 5. 最小数据模型

一个应用只有一个发布入口，发布事实直接附着在现有 `ai_app`，不新增一对一发布表。

追加字段：

| 字段 | 类型 | 约束 | 含义 |
| :--- | :--- | :--- | :--- |
| `publish_code` | `varchar(32)` | 可空、全局唯一 | 首次发布生成的 32 位无横线随机 UUID |
| `publish_status` | `varchar(16)` | 非空，默认 `UNPUBLISHED` | `UNPUBLISHED | PUBLISHED` |
| `published_at` | `datetime` | 可空 | 最近一次成功发布的时间 |

设计理由：

- `publish_code` 是路由标识，不是权限凭据；即使链接被转发，用户仍必须登录并通过应用授权。
- 32 位随机 UUID 不暴露雪花 ID，且不可实际枚举。
- 全局唯一索引使发布代码可以在未知租户时进行一次精确解析。
- 下线时保留 `publish_code`，重新发布不换链接。
- 名称、头像和描述继续以 `ai_app` 为唯一事实来源，不复制“发布名称/发布头像”。

Liquibase 只能在 `lambda-ai-changelog.xml` 末尾追加 changeSet，不能改写已有 changeSet。建议索引名：

```text
uk_ai_app_publish_code(publish_code)
```

`AppEntity` 同时补齐已经存在于表中的 `tenantId` 映射，供受控的发布代码解析获得真实租户；业务创建时仍不得手工填充租户字段。

### 5.1 更新与回退（显式无版本机制）

本节明确「生产后怎么更新、怎么回退」，并给出**不引入版本表**的依据。三类变化的路径完全不同，必须分开：

| 变化类型 | 更新 | 回退 | 是否需版本 |
| :--- | :--- | :--- | :--- |
| 代码缺陷（后端 jar 坏） | 部署新 jar，Liquibase 自动执行 changeSet | 重新部署上一个 jar（包级回滚） | 否：应用发布问题，由 CI/CD + 镜像版本解决 |
| 紧急止血（已发布应用出问题） | — | `enabled=false` 立即挡住发布页+后台新回合；或下线仅关链接 | 否：§4 三态分离已覆盖的运行时手段 |
| 配置改坏（提示词/工具/模型改错） | 编辑保存 → `ConfigChangedEvent` 失效缓存 → 下一新回合重建生效 | 把字段改回正确值再保存（再次失效缓存） | **唯一痛点** |

机制事实：运行配置按 `(appId, tenantId)` 缓存进 `AgentFactory`，构建期固化进 Agent 实例；改配置触发 `invalidateApp`，**下一个新回合**重建，进行中的回合不受影响，多实例经 Dubbo broadcast 同步失效。代码回滚（重部署）与运行时止血（停用/下线）均已覆盖——真正缺的只有「配置内容的撤销」：`ai_app` 只存当前值，改错无法自动回到上一个正确值。

**决策：不引入发布版本表**（见 §3.2）。版本表服务于灰度/多渠道/多环境，本期明确不做；为一个被排除的需求预埋版本会复制 `ai_app` 这唯一事实来源，违反单一事实来源与最小充分设计。

**配置变更审计（append-only，非版本）**：为解决「改错能查回上一个值」，新增配置审计，每次创建/更新/删除把**变更前**的配置快照追加到一张只增表：

- 表 `ai_app_config_audit`：`id / tenant_id / app_id / operation(CREATE|UPDATE|DELETE) / config_json(变更前快照) / operator / created_at`，索引 `(tenant_id, app_id)`。
- 在 `AppServiceImpl.update/delete` 同一事务内、**改库前**写一条审计；create 记录初始快照。失败随业务事务回滚，不产生半状态。
- **它不是版本机制**：不改变「编辑即生效」语义、不参与运行时读取、不构成第二份事实来源；仅供管理端查询历史，人工把字段改回正确值即完成回退。
- 仅当出现「一键指回某历史配置」的真实高频需求时，才评估把审计升级为可指回指针——届时才有可验证的并存需求，符合最小充分设计。

## 6. 后端领域与接口设计

### 6.1 模型

新增三个窄模型，禁止把 `AppEntity` 直接作为发布接口响应：

```text
AppPublication
  appId
  publishCode
  publishStatus
  publishedAt

PublishedAppProfile             # 匿名安全视图
  publishCode
  name
  avatar
  description

AvailableApp                    # 登录后的聊天安全视图
  id
  name
  avatar
  description
  appType
  supportsVision
```

`PublishedAppProfile` 不返回 `tenantId`。租户必须由服务端根据发布代码解析，不能把客户端传入的租户 ID 当成权威事实。

### 6.2 管理接口

均放在 `/v1/ai/apps/{appId}/publication`，并保持 `ROLE_DEV`：

| 方法 | 路径 | 语义 |
| :--- | :--- | :--- |
| `GET` | `/v1/ai/apps/{appId}/publication` | 查询发布状态和代码 |
| `PUT` | `/v1/ai/apps/{appId}/publication` | 幂等发布；首次生成代码 |
| `DELETE` | `/v1/ai/apps/{appId}/publication` | 幂等下线；保留代码 |

发布事务：

1. 在当前租户内按 ID 锁定应用行。
2. 校验应用存在、已启用、模型仍有效、`audience` 合法。
3. `publishCode == null` 时生成随机 UUID；唯一索引冲突时重新生成并重试有限次数。
4. 设置 `PUBLISHED` 和 `publishedAt`。
5. 返回 `AppPublication`。

并发发布通过同一应用行锁串行化；不增加版本表、命令表或额外幂等账本。

后端只返回 `publishCode`，不保存也不拼接部署域名。管理前端用当前 `window.location.origin`、路由 base 和 `router.resolve({ name: 'PublishedAiApp', params: { publishCode } })` 生成可复制的绝对 URL，避免把环境地址写进数据库。

### 6.3 发布页接口

| 登录要求 | 方法 | 路径 | 响应/职责 |
| :--- | :--- | :--- | :--- |
| 否 | `GET` | `/v1/ai/public/apps/{publishCode}/profile` | 返回 `PublishedAppProfile`（单行精确查询，见 §6.4） |
| 是 | `GET` | `/v1/ai/public/apps/{publishCode}/access` | 校验发布态、启用态和受众，返回 `AvailableApp` |

只有 `profile` 使用 `@SaIgnore`。Session、Run、附件和 `access` 都继续受现有 Sa-Token 保护。

公开资料错误语义：

| 条件 | 页面表现 |
| :--- | :--- |
| 代码不存在 | “链接无效或应用不存在” |
| 已下线 | “应用已下线” |
| 应用停用 | “应用暂不可用” |
| 登录用户无受众权限 | “当前账号无权访问”，不能循环弹登录框 |

### 6.4 发布代码解析：单行精确查询，不建租户上下文

匿名 profile 要回答的问题极简——“这个 `publish_code` 对应的应用名称/头像/描述是什么”。回答它**不需要租户上下文**：`publish_code` 全局唯一，用一条跨租户精确单行查询即可拿到整行（含 `name/avatar/description`）。**租户是查询的结果，不是查询的前提。**

因此本节明确**不引入** `PublishedAppContextFilter`、`PublishedAppRequestContext`、`X-AI-Publish-Code` Header、ThreadLocal 设置与清理、登录前租户解析。把“查到的 `tenantId` 回灌上下文去帮助后续查询”是本末倒置：

- 匿名 profile：单行查询已返回全部所需字段，无需租户。
- 登录：`/auth/login` 按用户查出其**真实**租户（`prepareLoginUser` 本来如此）。把 publishCode 的租户塞给登录会反转身份事实。
- access / 会话 / 对话：登录后 `AuthUtils.getTenantId()` 即真实租户，与 publishCode 无关。

匿名 profile 的唯一跨租户例外（与既有约定一致）：

- `AppMapper` 增加明确命名的跨租户精确查询 `selectByPublishCode`，使用 `@InterceptorIgnore(tenantLine = "true")`。
- 只按具有全局唯一索引的高熵 `publish_code` 命中一行，禁止列表、模糊查询或客户端传租户 ID。
- 代码注释说明这是“发布入口公开资料查询”的受控跨租户例外。
- 该查询**只**用于匿名 profile；登录后的 access、会话等一律在登录租户上下文内按普通查询进行，不再使用此例外。

登录链路完全复用现有后台登录，发布页不携带任何发布相关 Header，不为登录建立额外租户上下文。

### 6.5 发布请求与目标应用绑定

发布页必须固定在单个应用上，不能让同租户下的另一个应用混入当前独立页面。删除 Filter 后，应用绑定改由 **access 返回的 `AvailableApp.id`** 承载，不再依赖请求上下文：

- 前端登录并调用 `access` 成功后，以响应中的 `AvailableApp.id` 作为后续创建会话、分页会话、对话的唯一 `appId`。
- 服务端在会话、消息、Run、附件入口继续按 `tenant_id + user_id` 隔离并校验 Session 归属当前用户；发布页前端只传 access 给定的 appId，不引入额外的发布上下文校验层。
- `AppService.loadAvailable(appId)` 在 access 内做 `enabled + audience + ownerId` 校验；不重复解析发布代码或另写受众判断。

签名附件预览仍按现有短期签名 URL 鉴权，不在 URL 中追加发布代码。下线关闭后续发布入口，但不强杀已经开始的 Run，符合 §4 的状态语义。

### 6.6 可见性单一事实来源

`audience / ownerId` 的解释必须继续只存在于 `AppService`：

- `PublishedAppService.access` 在登录租户上下文内按 `publishCode` 找到 appId，再调用 `AppService.loadAvailable(appId)`；不重复解析发布代码或受众规则。
- 不在 Controller、拦截器和前端各写一套 B/C/ALL 判断。
- `GET /available` 与发布 `access` 都使用同一个 `AvailableApp` 转换。

## 7. 发布与访问流程

```text
开发者                         浏览器发布页                       后端
  | PUT /apps/{id}/publication    |                                |
  |------------------------------>| 生成/返回 publishCode           |
  |<------------------------------|                                |
  |                                                               |
  | 分享 /app/{publishCode}       |                                |
  |------------------------------>| GET /public/apps/{code}/profile|
  |                               |------------------------------->|
  |                               |<---- 名称/头像/描述 ------------|
  |                               | 打开独立登录 Dialog             |
  |                               | POST /auth/login (复用现有登录)  |
  |                               |------------------------------->|
  |                               |<----------- accessToken --------|
  |                               | GET /public/apps/{code}/access  |
  |                               |------------------------------->|
  |                               |<----------- AvailableApp -------|
  |                               | GET/POST /sessions... (appId 取自 access) |
  |                               | POST SSE /sessions/{id}/chat    |
```

关键点：

- 页面资料加载成功不等于获得对话权限。
- 登录成功后必须再调用 `access`，不能只凭“拿到 Token”解锁输入框。
- 发布页不携带任何发布相关 Header；后续会话/对话的 appId 取自 access 返回的 `AvailableApp.id`。
- 发布页下线只关闭分发入口；需要立即停止所有入口时使用现有 `enabled=false`。

## 8. 独立登录设计

### 8.1 为什么不能直接调用当前 `authStore.authLogin`

当前后台登录成功后会：

- 获取用户信息；
- 获取权限码；
- 加载字典；
- 连接全局 SSE；
- 跳转后台首页或 redirect；
- 展示后台登录成功通知。

发布页只需要 Token、最小用户信息和应用访问校验，直接调用会让独立页重新耦合后台。

### 8.2 复用方式

把凭据登录和验证码挑战提取成无路由副作用的共享能力：

```text
useCredentialLogin
  login(credentials) -> token | captchaRequired
  loadCaptcha()
  loginWithCaptcha(credentials, token, code)
```

发布页与后台登录使用同一套凭据/验证码流程，不携带任何发布相关 Header；登录身份与租户完全由现有登录链路决定。

后台登录页和发布页登录弹窗共同调用它，但分别处理成功后的编排：

| 场景 | 登录成功后 |
| :--- | :--- |
| 后台 | 权限码、字典、全局 SSE、后台跳转 |
| 发布页 | 保存 Token、查询 userinfo、调用发布 access、原地关闭弹窗 |

### 8.3 发布页认证状态机

```text
BOOTSTRAPPING
  ├── profile 失败 ----------------> NOT_FOUND / OFFLINE
  └── profile 成功
        ├── 无 Token --------------> ANONYMOUS + LOGIN_DIALOG_OPEN
        └── 有 Token -> access
              ├── 成功 ------------> READY
              ├── 401 -------------> ANONYMOUS + LOGIN_DIALOG_OPEN
              └── 403 -------------> FORBIDDEN

READY -- Token 失效 --> ANONYMOUS + LOGIN_DIALOG_OPEN
READY -- 主动退出 --> ANONYMOUS
```

行为要求：

- 首次匿名进入自动打开弹窗；用户可以关闭弹窗浏览应用介绍，但输入区保持锁定。
- 点击锁定输入区或顶部“登录”再次打开弹窗。
- Token 失效时留在原 URL，不跳后台登录页。
- 401 时只断开当前页面 SSE 订阅，不调用停止 Run；同一用户重新登录后沿用现有恢复机制。
- 403 表示账号没有应用权限，不能当成未登录反复要求输入密码。
- 主动退出后清空当前页面会话和消息，保留公开资料。

### 8.4 路由与请求拦截

发布路由建议为：

```text
/app/:publishCode
```

它作为不带 Layout 的 external route 注册，路由元信息明确声明：

```text
standalone: true
ignoreAccess: true
authPresentation: dialog
```

当前路由守卫只在“没有 Token”时处理 `ignoreAccess`；有 Token 时仍会加载后台动态路由、用户权限和字典。守卫必须在后台权限初始化之前优先放行 `standalone` 路由。

当前请求客户端遇到 401 会调用后台 `logout()` 并跳 `/auth/login`。需要增加发布页认证协调器：

- 当前路由 `authPresentation=dialog` 时，清理失效 Token、通知发布页打开登录弹窗，不进行路由跳转。
- 普通后台路由继续沿用原行为。
- `chat-panel.vue` 的原生 SSE fetch 也把 401 交给同一个协调器，不能单独实现另一套重认证判断。

发布页不引入任何自定义发布 Header，因此无需为其调整 CORS `Access-Control-Allow-Headers`。

## 9. 前端组件边界

### 9.1 应用壳与 AI 聊天组件分工

发布路由和认证属于具体应用壳，聊天能力属于 `system-ui`：

```text
apps/web-tdesign
└── src/views/_core/published-ai-app/
    ├── index.vue                         独立路由、公开资料、认证编排
    └── published-login-dialog.vue        TDesign 登录弹窗

packages/system-ui/src/views/ai/chat/
├── chat-panel.vue                        现有对话内核，增加 standalone 展示模式
├── chat-session-list.vue                 从后台 index.vue 提取的会话列表
└── published-chat-workspace.vue          单应用会话侧栏 + ChatPanel
```

这样 `system-ui` 不导入 `#/store`、`#/api` 等 web-tdesign 私有别名，应用壳也不复制聊天状态机。

### 9.2 `chat-panel.vue` 的必要收敛

不重写聊天面板，只增加必要的展示/上下文边界：

```text
props
  app
  session
  presentation: embedded | standalone

events
  sessionCreated
  messageSent
  authRequired
```

`standalone` 模式：

- 隐藏“最大化”按钮，因为页面本身已占满视口；
- 页面级 Header 展示应用名称，面板内部不重复标题栏；
- 消息内容宽度限制在约 800px 并水平居中；
- 输入区与消息内容使用相同宽度；
- 继续使用现有 `useChat`、AG-UI、Run 恢复和 HITL 代码。
- `app` 由发布页 access 响应提供（含 `AvailableApp.id`）；REST、附件和原生 SSE fetch 不携带任何发布 Header，与后台聊天走同一套请求客户端。

### 9.3 发布管理界面

当前表单中的“发布设置”应调整语义：

- `enabled` 归入基本信息/运行设置；
- `audience` 归入“访问控制”；
- 真正发布动作使用独立 `AppPublishDialog`，不和“保存应用配置”混成一个按钮。

应用卡片/表格建议增加：

- 发布状态 Tag：未发布、已发布、已发布但应用停用；
- “发布”或“访问”操作；
- 发布成功后显示只读 URL、复制按钮、在新窗口打开按钮；
- 已发布状态提供带确认的“下线”按钮。

新建应用必须先保存再发布，发布按钮不能隐式提交未保存的五个配置 Tab。

## 10. 发布后页面信息架构

### 10.1 桌面端

```text
┌──────────────────────┬──────────────────────────────────────────────┐
│  应用头像  应用名称   │  应用名称                         用户菜单   │
│                      ├──────────────────────────────────────────────┤
│  ＋ 新建对话          │                                              │
│                      │          [应用头像]                          │
│  今天                │          我是 {应用名称}                     │
│   会话标题 A          │          {应用描述}                          │
│   会话标题 B          │                                              │
│  更早                │          TDesign Chat 消息流                 │
│   会话标题 C          │                                              │
│                      │                                              │
│                      │     ┌──────────────────────────────────┐     │
│  用户头像  用户名     │     │ ChatSender                      │     │
└──────────────────────┴─────┴──────────────────────────────────┴─────┘
```

布局接近 ChatGPT 的原因是其层级适合长对话：会话导航固定在左侧、正文保持窄列、输入框贴近底部。视觉实现仍使用 TDesign 设计变量，不复制 ChatGPT 的品牌色或组件。

### 10.2 匿名锁定态

- 页面仍展示应用头像、名称和描述。
- 会话侧栏不请求任何会话 API，显示“登录后查看历史会话”。
- 中央保持欢迎空态。
- `ChatSender` 禁用，覆盖“登录后开始对话”按钮。
- 自动打开宽约 420px 的 TDesign Dialog。

登录弹窗使用：

- `Dialog`
- `Form` / `FormItem`
- `Input` / password Input
- `Button`
- `Loading`
- 图片验证码仍在同一 Dialog 内切换，不再弹第二个后台风格窗口。

### 10.3 登录后聊天态

TDesign Chat 组件映射保持现有实现：

| 内容 | 组件 |
| :--- | :--- |
| 用户/助手消息 | `ChatItem` |
| Markdown / 文本 | `ChatContent` |
| 推理过程 | `ChatReasoning` |
| 工具调用 | `ToolCallRenderer` |
| 加载状态 | `ChatLoading` |
| 输入与停止 | `ChatSender` |
| 图片和文档 | `Attachments` |
| 状态管理/AG-UI | `useChat` |

发布页只调整容器和 design token：

- 页面背景：TDesign page background token；
- 侧栏和输入容器：TDesign container background token；
- 边框、文字、品牌色、hover、dark mode 均使用 TDesign token；
- 不新增 Ant Design、Naive UI、Element Plus 或自制基础控件；
- Tailwind 只承担布局、尺寸与响应式，不另建一套颜色系统。

用户消息建议改成中性浅色、右侧短气泡；助手消息保持正文式无大色块。这样更接近 ChatGPT 的阅读体验，也比当前后台页的大面积蓝色用户气泡更适合长对话。

### 10.4 移动端

- 会话侧栏改为 TDesign `Drawer`，由顶部菜单按钮打开。
- Header 只保留菜单、应用头像/名称、登录或用户菜单。
- 消息区和输入区占满可用宽度，保留安全区和软键盘空间。
- 登录 Dialog 宽度使用视口自适应，不出现横向滚动。
- 输入区使用 `position: sticky` 或页面内部固定布局，不遮挡最后一条消息。

## 11. 安全边界

### 11.1 必须保证

- 匿名只可读取 `PublishedAppProfile`。
- 公开接口不返回应用 ID、系统提示词、模型 ID、工具、MCP、知识库、技能或子代理配置。
- 发布代码不可替代登录和授权。
- 匿名 profile 的跨租户查询只允许按唯一 `publish_code` 精确命中一行，禁止列表/模糊查询或客户端传租户 ID。
- access、会话、消息、Run、附件一律在登录租户上下文内进行；跨租户应用因租户插件过滤天然不可见，无需单独的租户比对。
- 会话和附件继续按当前用户所有权校验。
- 发布页不能绕过工具白/黑名单、HITL 或应用禁用检查。
- 日志不得记录密码、Token、验证码、用户凭据或完整工具参数。

### 11.2 Markdown 与外部内容

发布页面向更广的登录用户，必须继续经 `ChatContent` 渲染 Markdown，禁止新增 `v-html`。实施时应验证当前 Cherry Markdown 配置不会执行不受信任 HTML/脚本，并为外链设置安全打开策略。

### 11.3 WORKSPACE 应用

WORKSPACE 应用发布后仍可能使用沙箱、工具和自演化能力。发布确认框应展示风险提示，但本期不新增第二套工具权限系统：

- 工具可用性继续由应用 allow/deny 决定；
- 有副作用的工具继续依赖现有 `@RequireConfirm` / HITL；
- 若运营方需要立即阻止所有用户继续开启回合，应停用应用，而不只是下线链接。

## 12. 文件级改造建议

### 12.1 后端

| 文件/目录 | 改造 |
| :--- | :--- |
| `apps/model/entity/AppEntity.java` | 增加 tenant/publish 字段映射 |
| `apps/model/entity/AppConfigAuditEntity.java` | 配置审计实体（append-only） |
| `apps/mapper/AppConfigAuditMapper.java` | 审计表 Mapper（仅插入与按应用查询） |
| `AiConstants.java` | 增加 `AppAudience`、`PublishStatus` 枚举 |
| `apps/model/` | 增加 `AppPublication`、`PublishedAppProfile`、`AvailableApp` |
| `apps/mapper/AppMapper.java` | 增加按发布代码的受控精确查询 |
| `apps/service/AppService*` | 收敛受众校验和安全视图转换；更新/删除前写配置审计 |
| `apps/service/AppPublicationService*` | 发布、下线、公开资料（单行精确查询）、登录后授权访问 |
| `apps/controller/AppPublicationController.java` | `ROLE_DEV` 发布管理接口 |
| `apps/controller/PublishedAppController.java` | 匿名 profile 与登录后 access |
| `AiErrorCode.java` | 增加最少必要的发布状态错误码 |
| `lambda-ai-changelog.xml` | 追加字段和唯一索引 changeSet |

### 12.2 前端

| 文件/目录 | 改造 |
| :--- | :--- |
| `apps/web-tdesign/src/router/routes/` | 注册 standalone external route |
| `apps/web-tdesign/src/router/guard.ts` | standalone 路由不初始化后台权限 |
| `apps/web-tdesign/src/api/request.ts` | 401 原地弹登录（发布路由），不注入任何发布 Header |
| `apps/web-tdesign/src/views/_core/published-ai-app/` | 发布页壳和独立登录 Dialog |
| `apps/web-tdesign/src/views/_core/authentication/login.vue` | 复用提取后的凭据/验证码能力 |
| `packages/system-ui/src/api/ai/app.ts` | 发布 API、安全视图类型 |
| `packages/system-ui/src/views/ai/apps/` | 发布 Dialog、状态和操作 |
| `packages/system-ui/src/views/ai/chat/index.vue` | 复用提取后的会话列表 |
| `packages/system-ui/src/views/ai/chat/chat-panel.vue` | standalone 展示和统一 401 事件 |
| `packages/system-ui/src/views/ai/chat/` | 新增单应用发布聊天工作区 |

## 13. 分阶段实施

### Cycle 1：后端发布事实

- 追加 Liquibase 字段和唯一索引。
- 增加发布状态、严格 audience 校验、管理接口。
- 增加公开安全 DTO 和 profile/access 接口（profile 用单行精确查询，见 §6.4）。
- 把 `/available` 改为 `AvailableApp`，消除内部配置暴露。
- 新增 `ai_app_config_audit` 表与实体；`AppService` 更新/删除前写变更前快照（含 create 初始快照）。
- 完成发布、下线、权限、跨租户精确查询、DTO 安全和配置审计追加测试。

验证：

```shell
mvn -pl lambda-fusion-ai test
mvn -pl lambda-fusion-ai -am compile
```

### Cycle 2：后台发布管理

- 增加发布状态、发布 Dialog、复制/访问/下线交互。
- 调整现有“发布设置”的命名与字段归属。
- 保持保存和发布为两个明确动作。

验证：system-ui typecheck、lint，人工验证首次发布和重复发布 URL 不变。

### Cycle 3：独立页面与登录

- 注册 standalone route。
- 提取共享凭据/验证码逻辑。
- 实现发布页登录 Dialog、发布请求上下文和 401 原地重认证。
- 未登录只加载 profile，不触发会话、权限码、字典或全局 SSE 请求。

验证：匿名、错误密码、验证码、Token 失效、无权限、下线、停用场景。

### Cycle 4：聊天工作区复用与视觉收敛

- 提取会话列表。
- 为 `chat-panel.vue` 增加 standalone 模式。
- 完成桌面侧栏、移动 Drawer、TDesign token 和 ChatGPT 式内容宽度。
- 回归流式、停止、恢复、HITL、图片和文档附件。

## 14. 验收标准

- 访问发布 URL 时无后台 Layout，未登录不会跳 `/auth/login`。
- 匿名网络请求只有公开 profile；会话、消息、Run 和附件接口均不可匿名访问。
- 登录弹窗在当前页面完成用户名、密码和验证码流程。
- 登录成功后不加载后台菜单、权限码、字典或全局用户 SSE。
- 当前账号无受众权限时显示 403 状态，不反复弹登录。
- 发布、下线和重新发布幂等，重新发布 URL 不变。
- 下线链接不影响后台调试；停用应用同时阻止发布页和后台开启新回合。
- 配置更新/删除在 `ai_app_config_audit` 留下变更前快照，可据以人工回退；审计不参与运行时读取、不改变编辑即生效语义。
- 公开响应中不存在系统提示词、模型、工具、MCP、知识库、技能和子代理信息。
- 发布页完整复用现有 AG-UI、Run 恢复、HITL 和附件能力。
- 桌面和移动端只使用 TDesign/TDesign Chat 可见组件，dark mode 正常。
- 后端测试与 compile、前端 typecheck/lint 全部通过。

## 15. 实施前置说明

前端仓库 `AGENTS.md` 要求新增能力先读取 `openspec/AGENTS.md` 并按 OpenSpec 建立 change proposal，但当前工作区缺少该文件。正式进入前端编码前，应先恢复/补齐该规则文件或由维护者确认现有 OpenSpec 约定，再创建对应 change；本设计不臆造缺失规则。
