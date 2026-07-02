package com.lambda.fusion.config.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName(value = "la_configs", autoResultMap = true)
@Schema(description = "配置信息")
@NoArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class ConfigEntity {

    public ConfigEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }

    @Schema(description = "配置信息编号")
    @TableId("PROPERTY_ID")
    private String id;

    @Schema(description = "配置信息名称")
    @TableField("PROPERTY_NAME")
    private String name;

    @Schema(description = "配置信息键")
    @TableField("PROPERTY_KEY")
    private String key;

    @Schema(description = "配置信息值")
    @TableField("PROPERTY_VALUE")
    private String value;

    @Schema(description = "配置信息类型")
    @TableField("PROPERTY_TYPE")
    private Integer type;

    @Schema(description = "配置信息描述")
    @TableField("PROPERTY_DESC")
    private String description;

    @Hidden
    @TableField("APPLICATION")
    @JsonProperty("module")
    private String application;

    @Hidden
    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    @Schema(description = "配置选项列表")
    private List<ConfigOptionEntity> options;
}
