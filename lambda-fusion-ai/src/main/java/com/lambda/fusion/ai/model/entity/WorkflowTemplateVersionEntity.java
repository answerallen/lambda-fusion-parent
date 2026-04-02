package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作流模板版本历史实体
 * 用于存储模板的历史版本，支持版本回滚和对比
 */
@Data
@TableName("ai_workflow_template_version")
public class WorkflowTemplateVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的模板ID
     */
    private Long templateId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 版本描述/变更说明
     */
    private String description;

    /**
     * 模板定义JSON
     */
    private String definition;

    /**
     * 输入参数定义JSON Schema
     */
    private String inputSchema;

    /**
     * 输出参数定义JSON Schema
     */
    private String outputSchema;

    /**
     * 变量定义
     */
    private String variables;

    /**
     * 创建此版本的用户
     */
    private Long createBy;

    /**
     * 版本创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer deleted;
}
