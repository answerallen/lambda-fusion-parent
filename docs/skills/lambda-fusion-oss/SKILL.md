---
name: "lambda-fusion-oss"
description: "附件上传与管理模块（附件分组、上传删除、预签名访问、OSS 客户端选择、多租户隔离）。当需求涉及文件上传或附件元数据管理时调用。"
---

# Lambda Fusion OSS

## 模块引入
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-oss</artifactId>
</dependency>
```

## 自动配置
- 自动配置入口：`lambda-fusion-oss/src/main/java/com/lambda/fusion/autoconfig/UploadAutoConfiguration.java`
- 模块装配类：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/UploadConfigure.java`
- AutoConfiguration imports：`lambda-fusion-oss/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Liquibase 变更脚本：`lambda-fusion-oss/src/main/resources/META-INF/db/changelogs/lambda-upload-changelog.xml`

## 配置
- 当前模块本身没有独立的 `@ConfigurationProperties` 前缀
- OSS 客户端、存储桶和鉴权相关配置来自 `lambda-cloud-starter-oss`
- 模块运行依赖 starter 提供的 `OssClientManager`

## 主要能力
- 附件上传、删除、详情查询
- 附件预签名访问地址生成
- 附件分页查询与可选预览地址返回
- OSS 客户端列表查询与客户端切换上传
- 附件分组增删改查
- 按租户隔离附件与分组数据

## 入口定位
- REST 控制器：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/controller/AttachmentController.java`
- 服务接口：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/service/AttachmentService.java`
- 服务实现：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/service/impl/AttachmentServiceImpl.java`
- 附件 Mapper：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/mapper/AttachmentMapper.java`
- 分组 Mapper：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/mapper/AttachmentGroupMapper.java`
- 查询模型：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/model/AttachmentQuery.java`
- 展示模型：`lambda-fusion-oss/src/main/java/com/lambda/fusion/upload/model/AttachmentView.java`

## 关键机制
- 上传时先校验附件分组，再通过 `OssClientManager` 获取指定 `clientName` 的 OSS 客户端执行上传
- 附件对象 key 按 `attachments/yyyyMMdd/uuid-fileName` 规则生成
- 查询、删除、分组操作都带有当前租户隔离逻辑
- 详情接口默认补充预签名访问地址；分页接口可通过查询参数决定是否生成预签名地址
- 删除附件时会优先尝试删除 OSS 对象，再删除本地元数据记录

## REST 能力概览
- `POST /upload/attachments/{groupId}/upload`
- `DELETE /upload/attachments/{id}`
- `GET /upload/attachments/{id}`
- `GET /upload/attachments/{id}/preview-url`
- `GET /upload/attachments/page`
- `GET /upload/attachments/clients`
- `GET /upload/attachments/groups`
- `POST /upload/attachments/groups`
- `PUT /upload/attachments/groups/{id}`
- `DELETE /upload/attachments/groups/{id}`

## 条件与依赖说明
- 模块依赖 `lambda-cloud-starter-oss` 提供 OSS 客户端管理能力
- 模块依赖 MyBatis 与数据库表保存附件、分组元数据
- 当前控制器未直接暴露批量删除接口，若需要该能力，需要同时补控制器和调用约束

## 常见改造入口
1. 调整上传、删除、预签名逻辑时，优先检查 `AttachmentServiceImpl`。
2. 调整分页筛选、预览地址返回策略或租户隔离时，检查 `AttachmentQuery`、`AttachmentServiceImpl`、相关 mapper。
3. 调整分组规则或分组校验时，检查 `AttachmentGroupMapper`、`UpsertAttachmentGroup`、`AttachmentServiceImpl`。
4. 调整 OSS 客户端选择或多客户端策略时，检查 `clientName` 传参路径和 `OssClientManager` 的使用方式。
