package com.lambda.fusion.auth.resource.bean;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 单一资源信息
 */
@Getter
@Setter
@Schema(description = "资源元数据")

public class MutableResource {
    @Schema(required = true, description =  "资源编号")
    private String id;
    @Schema(required = true, description =  "资源名称")
    @JsonProperty("name")
    private String resName;
    @Schema(description = "访问路径")
    @JsonProperty("path")
    private String resPath;
    @Schema(description = "资源路径")
    @JsonProperty("url")
    private String resUrl;
    @Schema(description = "上级资源")
    private String parentId;
    @Schema(required = true, description =  "资源级别")
    @JsonProperty("rank")
    private int resRank;
    @Schema(required = true, description =  "资源顺序")
    private int orderNo;
    @Schema(description = "资源图标")
    private String ico;
    @Schema(description = "按钮方法函数")
    private String method;
    @Schema(description = "是否隐藏")
    private boolean hidden = false;
    @Schema(description = "资源类型.1：菜单，2：外链, 3: 按钮")
    @JsonProperty("type")
    private Integer resType;
    @Hidden
    private String parentkeys;
    @Schema(description = "资源模式.0: 后台资源, 1: app资源")
    @JsonProperty("mode")
    private Integer resMode;
    @Schema(description = "备注信息")
    private String remark;
    @Schema(description = "国际化key字段")
    private String keyName;
    @Schema(description = "是否缓存")
    private boolean keepAlive;
    @Schema(description = "内置扩展字段")
    private String expand;
    @Schema(description = "业务扩展字段")
    private String businessExpand;

    @Schema(description = "是否有权限")
    private Boolean checked = true;

}
