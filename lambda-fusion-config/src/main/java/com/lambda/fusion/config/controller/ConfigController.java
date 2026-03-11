package com.lambda.fusion.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.config.handler.ConfigChangeHandler;
import com.lambda.fusion.config.model.*;
import com.lambda.fusion.config.refresh.DatabaseContextRefresher;
import com.lambda.fusion.config.service.ConfigService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置管理控制器
 *
 * <p>提供系统配置的完整管理功能，包括：
 * <ul>
 * <li>配置的增删改查操作</li>
 * <li>配置选项的管理</li>
 * <li>配置的批量操作</li>
 * <li>动态配置刷新</li>
 * </ul>
 *
 * <p>权限控制：
 * <ul>
 * <li>管理类操作需要 ROLE_DEV 角色</li>
 * <li>查询类操作对所有用户开放</li>
 * </ul>
 *
 * @since 1.0.0
 */
@SaCheckRole("ROLE_DEV")
@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ConfigController {

    /**
     * 当前应用名称，用于配置隔离
     */
    @Value("${spring.application.name}")
    private String application;

    /**
     * 数据库上下文刷新器，用于动态刷新配置
     */
    private final DatabaseContextRefresher contextRefresher;

    /**
     * 配置服务
     */
    private final ConfigService configService;

    /**
     * 配置变更服务
     */
    private final ConfigChangeHandler configChangeHandler;

    /**
     * 分页查询配置列表
     *
     * <p>支持按条件分页查询系统配置信息，主要用于管理后台的配置管理界面。
     *
     */
    @Operation(summary = "分页查询配置列表", description = "支持按条件分页查询系统配置信息，用于管理后台配置管理界面")
    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    public Page<ConfigEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid QueryConfigPage queryConfigPageDTO) {
        if (number != null) {
            queryConfigPageDTO.setPageNum(number);
        }
        if (size != null) {
            queryConfigPageDTO.setPageSize(size);
        }
        return configService.page(queryConfigPageDTO.getPage(), queryConfigPageDTO.getLambdaQueryWrapper());
    }

    /**
     * 查询配置列表
     *
     * <p>根据多种条件组合查询配置列表，支持精确匹配和模糊查询，不分页返回所有匹配结果。
     *
     */
    @Operation(summary = "查询配置列表", description = "支持按键名、ID列表、键列表等多种条件组合查询，用于前端组件数据加载和配置筛选")
    @GetMapping
    public List<ConfigEntity> listConfigs(@Valid QueryConfigList queryDTO) {
        // 兼容性处理：将逗号分隔的字符串参数转换为List格式
        if (queryDTO.getIds() == null && StringUtils.isNotBlank(queryDTO.getIdsString())) {
            queryDTO.setIds(Arrays.asList(queryDTO.getIdsString().split(",")));
        }
        if (queryDTO.getKeys() == null && StringUtils.isNotBlank(queryDTO.getKeysString())) {
            queryDTO.setKeys(Arrays.asList(queryDTO.getKeysString().split(",")));
        }
        return configService.listConfigs(queryDTO);
    }

    /**
     * 新增配置信息
     *
     * <p>创建新的系统配置项，支持同时保存配置基本信息和配置选项。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限，确保只有开发人员可以创建配置。
     *
     * @see SaveConfig 保存参数详细说明
     */
    @OperationLog
    @PostMapping
    @Operation(summary = "新增配置信息", description = "创建新的系统配置项，支持配置选项，需要开发者权限")
    public void save(@RequestBody @Valid SaveConfig source) {
        // 自动设置应用名称，实现配置隔离
        source.setApplication(application);
        configService.saveConfigWithOptions(source);
    }

    /**
     * 更新配置信息
     *
     * <p>根据配置ID更新配置的基本信息和选项，支持增量更新，只更新提供的字段。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @param id           配置ID，路径参数，不能为空
     * @param updateConfig 配置更新DTO，支持增量更新，通过参数校验
     * @see UpdateConfig 更新参数详细说明
     */
    @OperationLog
    @PutMapping("/{id}")
    @Operation(summary = "更新配置信息", description = "支持增量更新配置基本信息和选项，需要开发者权限")
    public void updateConfig(
            @Parameter(description = "配置ID，不能为空", required = true) @PathVariable String id,
            @Parameter(description = "配置更新信息", required = true) @RequestBody @Valid UpdateConfig updateConfig) {
        updateConfig.setId(id);
        configService.updateConfigWithOptions(updateConfig);
    }

    /**
     * 删除配置信息
     *
     * <p>根据配置ID删除配置及其所有相关选项，操作不可逆。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     */
    @OperationLog
    @DeleteMapping("/{id}")
    @Operation(summary = "删除配置信息", description = "根据ID删除配置及其所有选项，操作不可逆，需要开发者权限")
    public void delete(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        ConfigEntity target = configService.getById(id);
        LogContext.setDetail("DELETE: " + target.getKey());
        configService.removeById(id);
        // 级联删除配置选项
        if (CollectionUtils.isNotEmpty(target.getOptions())) {
            Set<String> ids =
                    target.getOptions().stream().map(ConfigOptionEntity::getId).collect(Collectors.toSet());
            configService.removeConfigOptionsByIds(ids);
        }
    }

    /**
     * 应用配置刷新
     *
     * <p>触发系统配置的动态刷新，使配置变更立即生效，无需重启应用。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     */
    @OperationLog
    @PatchMapping("/refresh")
    @Operation(summary = "应用配置刷新", description = "触发系统配置动态刷新，使配置变更立即生效")
    public void refresh() {
        contextRefresher.refresh();
    }

    /**
     * 根据ID查询配置详情
     *
     * <p>获取指定配置的完整信息，包括基本信息和所有配置选项。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询配置详情", description = "根据ID获取配置完整信息，包括选项")
    public ConfigEntity getById(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        return configService.getById(id);
    }

    /**
     * 查询配置选项列表
     *
     * <p>获取指定配置的所有选项信息，用于展示配置的可选值。
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     */
    @GetMapping("/{id}/options")
    @Operation(summary = "查询配置选项列表", description = "获取指定配置的所有选项信息")
    public List<ConfigOptionEntity> getOptionsByConfigId(
            @Parameter(description = "配置ID", required = true) @PathVariable String id) {
        ConfigEntity configEntity = configService.getById(id);
        return configEntity.getOptions();
    }

    /**
     * 批量查询配置列表
     *
     * <p>根据应用名称和配置ID列表批量查询配置信息，主要用于系统间的配置同步。
     *
     */
    @Operation(summary = "批量查询配置列表", description = "根据应用名称和ID列表批量查询配置，用于系统间配置同步")
    @PostMapping("/batch")
    public List<ConfigEntity> batchQueryConfigs(@RequestBody @Valid QueryConfig queryConfig) {
        return configService.batchQueryConfigs(queryConfig);
    }

    /**
     * 查询当前系统配置
     *
     * <p>获取当前应用的所有配置信息，主要用于系统运维和监控。
     */
    @GetMapping("/systems")
    @Operation(summary = "查询当前系统配置", description = "获取当前应用的所有配置信息，用于运维和监控")
    public List<ConfigEntity> getSystemConfig() {
        QueryConfig queryConfig = new QueryConfig();
        queryConfig.setApplication(application);
        return configService.batchQueryConfigs(queryConfig);
    }

    /**
     * 批量更新系统配置
     *
     * <p>批量更新多个配置的值，操作完成后自动触发配置刷新，使更改立即生效。
     *
     */
    @PutMapping("/batch")
    @Operation(summary = "批量更新系统配置", description = "批量更新配置值并触发动态刷新，用于运维人员和管理员")
    public void batchUpdateConfigs(@RequestBody @Valid BatchUpdateConfig batchUpdateDTO) {
        configService.batchUpdateConfigs(batchUpdateDTO);
        // 执行配置变更后续处理
        configChangeHandler.handle();
        // 触发动态配置刷新
        contextRefresher.doRefresh();
    }

    /**
     * 更新配置选项信息
     *
     * <p>根据配置选项ID更新选项的详细信息，支持增量更新，保护关键字段不被修改。
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/options/{id}")
    @Operation(summary = "根据编号更新选项信息", description = "支持增量更新配置选项，保护关键字段，需要开发者权限")
    public ConfigOptionEntity updateOptions(
            @Parameter(description = "配置编号", required = true) @PathVariable String id,
            @Parameter(description = "配置信息", required = true) @RequestBody @Valid ConfigOption source) {
        ConfigOptionEntity target = configService.getConfigOptionById(id);
        BeanUtil.copyProperties(source, target);
        target.setPid(null);
        target.setApplication(null);
        configService.updateConfigOption(target);
        LogContext.setDetail("UPDATE: " + target.getName() + "=" + target.getValue());
        return target;
    }

    /**
     * 删除配置选项信息
     *
     * <p>根据配置选项ID删除指定的配置选项，操作不可逆，删除前会记录选项信息用于审计。
     *
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @DeleteMapping("/options/{id}")
    @Operation(summary = "根据编号删除选项信息", description = "物理删除配置选项，操作不可逆，需要开发者权限")
    public void deleteOptions(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ConfigOptionEntity target = configService.getConfigOptionById(id);
        LogContext.setDetail("DELETE: " + target.getName());
        configService.removeConfigOptionById(id);
    }
}
