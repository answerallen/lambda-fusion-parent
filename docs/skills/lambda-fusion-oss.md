---
name: "lambda-fusion-oss"
description: "面向附件上传/分组管理、OSS 客户端选择、预签名访问链接、租户隔离附件数据等需求的模块分析与改造指南。Invoke when 需求涉及文件上传/附件表/OSS 集成。"
---

# lambda-fusion-oss 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造附件上传、删除、分页查询、预览 URL（预签名）生成、附件分组管理时。
- 需要对接/切换 OSS 存储实现（通过 `OssClientManager` 获取不同 clientName）时。
- 需要强化附件的租户隔离、访问控制与审计字段填充时。

## 模块定位与边界

- 本模块提供“附件管理控制面 + OSS 存储适配”，并不提供底层 OSS SDK；底层由 `lambda-cloud-starter-oss` 提供。
- 租户隔离主要通过 AuthUtils 获取 tenantId 并在查询条件中追加过滤完成。

## 自动装配与入口

- 自动装配入口：UploadAutoConfiguration（lambda-fusion-oss: com.lambda.fusion.autoconfig.UploadAutoConfiguration）
- 模块配置类（MapperScan/ComponentScan）：UploadConfigure（com.lambda.fusion.upload.UploadConfigure）

## 对外 API（AttachmentController）

- REST 入口：AttachmentController（com.lambda.fusion.upload.controller.AttachmentController）
  - `POST /upload/attachments/{groupId}/upload`：上传（支持 `clientName` 选择 OSS 客户端）
  - `GET /upload/attachments/{id}/preview-url`：获取预签名访问地址
  - `GET /upload/attachments/page`：分页查询
  - `GET /upload/attachments/clients`：列出可用 OSS 客户端
  - `GET/POST/PUT/DELETE /upload/attachments/groups*`：分组管理

## Service 核心逻辑（AttachmentServiceImpl）

- 主要实现：AttachmentServiceImpl（com.lambda.fusion.upload.service.impl.AttachmentServiceImpl）
  - 上传：生成 objectKey（按日期分层）→ `ossClient.upload` → 落库 AttachmentEntity
  - 删除：先尝试 `ossClient.delete(objectKey)`，无论是否成功都删除数据库记录（当前实现忽略 OSS 删除异常）
  - 查询/分页：按 tenantId 条件过滤，并可选生成 previewUrl
  - 分组：校验 tenantId 一致性，避免跨租户引用分组

## 数据库与 Liquibase

- Changelog：lambda-upload-changelog.xml（lambda-fusion-oss/src/main/resources/META-INF/db/changelogs/）
- 表结构历史/初始化参考（docs/sql）：docs/sql/

## 常见改造任务指引

- **增强安全性（下载鉴权/防越权）**：以 tenantId 过滤为基线，补充 owner 校验或权限校验；必要时对 previewUrl 的有效期设置上限。
- **支持更多上传场景（批量/分片/回调）**：保持 AttachmentService 的接口收口，新增能力优先扩展 service 方法而非直接在 controller 堆逻辑。
- **对象 key 规范调整**：修改 buildObjectKey 规则时，注意历史数据的兼容与迁移策略。
