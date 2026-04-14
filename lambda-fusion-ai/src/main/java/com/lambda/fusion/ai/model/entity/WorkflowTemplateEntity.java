package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.fusion.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流模板实体
 * 用于存储可复用的 AI 流程编排模板
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("ai_workflow_template")
public class WorkflowTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 模板唯一编码
     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板分类
     */
    private String category;

    /**
     * 模板版本号
     */
    private String version;

    /**
     * 模板定义JSON（GraphDefinition 的 JSON 序列化）
     */
    private String definition;

    /**
     * 模板缩略图/预览图URL
     */
    private String previewImage;

    /**
     * 模板图标
     */
    private String icon;

    /**
     * 模板颜色标识
     */
    private String color;

    /**
     * 输入参数定义JSON Schema
     */
    private String inputSchema;

    /**
     * 输出参数定义JSON Schema
     */
    private String outputSchema;

    /**
     * 变量定义（模板中使用的变量列表）
     */
    private String variables;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 是否系统内置模板
     */
    private Boolean systemTemplate;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 状态：draft-草稿, published-已发布, deprecated-已废弃
     */
    private String status;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer deleted;
}
