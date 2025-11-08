package com.lambda.fusion.config.domain.dto;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.config.domain.entity.ConfigEntity;
import com.lambda.fusion.core.pagination.PaginationDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配置分页查询DTO
 *
 * <p>继承PageQueryDTO基类，提供统一的分页查询功能，支持按配置名称、键名、应用名称等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持配置名称模糊查询</li>
 * <li>支持配置键名模糊查询</li>
 * <li>支持应用名称精确查询</li>
 * <li>支持租户配置隔离</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置分页查询参数")
public class ConfigPageQueryDTO extends PaginationDTO<ConfigEntity> {

    @Schema(description = "配置信息键，支持模糊查询")
    private String key;

    @Schema(description = "配置名称，支持模糊查询")
    private String name;

    @Schema(description = "模块名称，精确匹配")
    private String application;

    @Schema(description = "是否只查询租户配置项, 0:否, 1:是")
    private Integer tenantOnly = 0;

    @Schema(description = "配置类型")
    private Integer type;

    @Schema(description = "配置描述，支持模糊查询")
    private String description;

    /**
     * 构建查询条件
     *
     * @return LambdaQueryWrapper查询条件
     */
    @Override
    public LambdaQueryWrapper<ConfigEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ConfigEntity> wrapper = new LambdaQueryWrapper<>();

        // 配置键模糊查询
        wrapper.like(StringUtils.isNotBlank(key), ConfigEntity::getKey, key);

        // 配置名称模糊查询
        wrapper.like(StringUtils.isNotBlank(name), ConfigEntity::getName, name);

        // 应用名称精确匹配
        wrapper.eq(StringUtils.isNotBlank(application), ConfigEntity::getApplication, application);

        // 配置类型精确匹配
        wrapper.eq(type != null, ConfigEntity::getType, type);

        // 配置描述模糊查询
        wrapper.like(StringUtils.isNotBlank(description), ConfigEntity::getDescription, description);

        // 默认按创建时间降序排序
        wrapper.orderByDesc(ConfigEntity::getId);

        return wrapper;
    }
}
