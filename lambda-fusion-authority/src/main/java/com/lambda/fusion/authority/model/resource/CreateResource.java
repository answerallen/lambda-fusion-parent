package com.lambda.fusion.authority.model.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 单一资源信息
 *
 */
@AutoConverter(target = Resource.class)
@Getter
@Setter
@Schema(description = "资源元数据")
@SuppressFBWarnings("EI_EXPOSE_REP")
public class CreateResource extends BaseDTO<Resource> {

    @JsonIgnore
    @Schema(hidden = true, description = "资源ID，仅在租户分库同步新增资源情况下使用")
    private String id;

    @NotBlank(message = "资源名称不能为空")
    @Schema(description = "资源名称")
    @JsonProperty("name")
    private String resName;

    @Schema(description = "访问路径")
    @JsonProperty("path")
    private String resPath;

    @Schema(description = "资源路径")
    @JsonProperty("url")
    private String resUrl;

    @Schema(description = "资源图标")
    private String icon;

    @Schema(description = "按钮方法函数")
    private String method;

    @Schema(description = "是否隐藏")
    private boolean hidden = false;

    @Min(0)
    @Schema(description = "资源类型, 0:接口,1:菜单,2:外链,3:按钮,以字典扩展为准")
    @JsonProperty("type")
    private Integer resType;

    @Schema(description = "资源模式.0: 后台资源, 1: app资源")
    private Integer mode = 0;

    @Schema(description = "资源模式.0: 后台资源, 1: app资源")
    private Integer resMode = 0;

    @Schema(description = "备注信息")
    private String remark;

    @Schema(description = "国际化key字段")
    private String keyName;

    @Hidden
    private String parentId;

    @Schema(description = "是否缓存")
    private boolean keepAlive;

    @Schema(description = "内置扩展字段")
    private String expand;

    @Schema(description = "业务扩展字段")
    private String businessExpand;

    private List<Button> buttons;

    public Integer getMode() {
        if (null != mode && mode != 0) {
            this.resMode = mode;
        }
        return resMode;
    }

    @Getter
    @Setter
    public static class Button {
        @NotNull
        @Schema(description = "资源名称")
        @JsonProperty("name")
        private String resName;

        @Schema(description = "资源模式.0: 后台资源, 1: app资源")
        private Integer resMode = 0;

        @NotNull
        @Schema(description = "资源路径")
        @JsonProperty("path")
        private String resPath;

        @Schema(description = "资源图标")
        private String icon;

        @Schema(description = "按钮方法函数")
        private String method;
    }
}
