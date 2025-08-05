package com.lambda.fusion.configs.domain.entity;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.configs.domain.vo.ConfigOptionVO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@TableName(value = "LA_CONFIG_OPTIONS", autoResultMap = true)
@Schema(description = "选项信息")
@NoArgsConstructor
public class ConfigOptionEntity {

    public ConfigOptionEntity(ConfigOptionVO source) {
        BeanUtil.copyProperties(source, this);
    }

    @Schema(description = "选项编号")
    @TableId("OPTION_ID")
    private String id;

    @Schema(description = "配置信息编号")
    @TableField("PROPERTY_ID")
    private String pid;

    @Schema(description = "选项信息名称")
    @TableField("OPTION_NAME")
    private String name;

    @Schema(description = "选项信息值")
    @TableField("OPTION_VALUE")
    private String value;

    @Schema(description = "配置信息描述")
    @TableField("OPTION_DESC")
    private String description;

    @Hidden
    @TableField("APPLICATION")
    private String application;
}
