package com.lambda.fusion.config.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("LA_SETTINGS_LAYOUT")
public class SettingsLayoutEntity {

    @TableId("LAYOUT_ID")
    private String id;

    @TableField("APPLICATION")
    private String application;

    @TableField("LAYOUT_JSON")
    private String layoutJson;

    @TableField("LAYOUT_VERSION")
    private Integer layoutVersion;

    @TableField("UPDATED_AT")
    private LocalDateTime updatedAt;
}
