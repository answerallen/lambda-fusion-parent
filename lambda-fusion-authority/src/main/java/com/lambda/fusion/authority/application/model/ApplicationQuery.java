package com.lambda.fusion.authority.application.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 应用分页查询参数
 */
@Getter
@Setter
@Schema(description = "应用分页查询参数")
public class ApplicationQuery extends PageQuery<ApplicationEntity> {

    /**
     * 应用名称
     */
    @Schema(description = "应用名称，支持模糊查询")
    @Size(max = 64, message = "应用名称长度不能超过64个字符")
    private String name;

    /**
     * Spring应用名
     */
    @Schema(description = "Spring应用名，支持模糊查询")
    @Size(max = 128, message = "Spring应用名长度不能超过128个字符")
    private String springApplicationName;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<ApplicationEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ApplicationEntity> wrapper = super.getLambdaQueryWrapper();
        wrapper.like(StringUtils.isNotBlank(name), ApplicationEntity::getName, name);
        wrapper.like(
                StringUtils.isNotBlank(springApplicationName),
                ApplicationEntity::getSpringApplicationName,
                springApplicationName);
        wrapper.eq(enabled != null, ApplicationEntity::getEnabled, enabled);
        return wrapper;
    }
}
