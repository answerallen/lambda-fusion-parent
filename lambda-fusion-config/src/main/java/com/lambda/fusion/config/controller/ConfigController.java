package com.lambda.fusion.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.config.core.DatabaseContextRefresher;
import com.lambda.fusion.config.domain.dto.*;
import com.lambda.fusion.config.domain.entity.ConfigEntity;
import com.lambda.fusion.config.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.config.service.ConfigChangedService;
import com.lambda.fusion.config.service.ConfigOptionService;
import com.lambda.fusion.config.service.ConfigService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ConfigController {

    /** 当前应用名称，用于配置隔离 */
    @Value("${spring.application.name}")
    private String application;

    /** 数据库上下文刷新器，用于动态刷新配置 */
    private final DatabaseContextRefresher contextRefresher;

    /** 配置服务 */
    private final ConfigService configService;

    /** 配置选项服务 */
    private final ConfigOptionService configOptionService;

    /** 配置变更服务 */
    private final ConfigChangedService configChangedService;

    /**
     * 分页查询配置列表
     *
     * <p>支持按条件分页查询系统配置信息，主要用于管理后台的配置管理界面。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>支持按配置名称模糊查询</li>
     * <li>支持按应用名称精确筛选</li>
     * <li>支持租户配置隔离</li>
     * <li>自动处理分页参数</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>接收分页参数和查询条件</li>
     * <li>创建分页对象，默认页码1，页大小20</li>
     * <li>调用服务层执行分页查询</li>
     * <li>返回分页结果包含总数和当前页数据</li>
     * </ol>
     *
     * @param number 当前页码，可选，默认1，必须为正整数
     * @param size 每页条数，可选，默认20，必须为正整数
     * @param configPageQueryDTO 查询条件DTO，包含配置名称、应用名称等查询条件，支持参数校验
     * @return 分页结果，包含配置实体列表和分页元数据
     *
     * @throws IllegalArgumentException 当分页参数不合法时抛出
     * @see ConfigPageQueryDTO 查询条件参数说明
     * @see Page MyBatis-Plus分页对象
     * @since 1.0.0
     */
    @Operation(
            summary = "分页查询配置列表",
            description = "支持按条件分页查询系统配置信息，用于管理后台配置管理界面",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码，默认1，必须为正整数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1", minimum = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数，默认20，必须为正整数，最大100",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            })
    @GetMapping({"/manager/page/{number:\\d+}", "/manager/page/{number:\\d+}/size/{size:\\d+}"})
    public Page<ConfigEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid ConfigPageQueryDTO configPageQueryDTO) {
        return configService.pageConfigs(new Page<>(number, size), configPageQueryDTO);
    }

    /**
     * 查询配置列表
     *
     * <p>根据多种条件组合查询配置列表，支持精确匹配和模糊查询，不分页返回所有匹配结果。
     *
     * <h3>支持的查询条件：</h3>
     * <ul>
     * <li><strong>按键名查询：</strong>支持右侧模糊匹配，如 "spring" 匹配所有以 "spring" 开头的配置键</li>
     * <li><strong>按ID列表查询：</strong>支持List&lt;String&gt;和逗号分隔字符串两种格式</li>
     * <li><strong>按键列表查询：</strong>支持List&lt;String&gt;和逗号分隔字符串两种格式</li>
     * <li><strong>按应用名查询：</strong>精确匹配应用名称</li>
     * </ul>
     *
     * <h3>参数处理逻辑：</h3>
     * <ol>
     * <li>优先使用List格式的参数</li>
     * <li>如果List为空且字符串参数不为空，则将逗号分隔字符串转换为List</li>
     * <li>空格会被自动去除，空字符串会被忽略</li>
     * <li>所有条件采用AND组合，即同时满足所有非空条件</li>
     * </ol>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>前端下拉框数据获取</li>
     * <li>配置项的批量操作预览</li>
     * <li>配置项的条件筛选</li>
     * </ul>
     *
     * @param queryDTO 查询条件DTO，所有字段均为可选，支持多种查询方式组合
     * @return 配置实体列表，如果没有匹配结果则返回空列表，不返回null
     *
     * @throws IllegalArgumentException 当参数格式错误时抛出
     * @see ConfigListQueryDTO 查询条件详细说明
     * @since 1.0.0
     */
    @Operation(summary = "查询配置列表", description = "支持按键名、ID列表、键列表等多种条件组合查询，用于前端组件数据加载和配置筛选")
    @GetMapping
    public List<ConfigEntity> listConfigs(@Valid ConfigListQueryDTO queryDTO) {
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
     * <h3>功能特性：</h3>
     * <ul>
     * <li>自动设置当前应用名称</li>
     * <li>配置键唯一性检查</li>
     * <li>支持多个配置选项</li>
     * <li>事务保证数据一致性</li>
     * <li>操作日志记录</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>接收配置保存DTO参数</li>
     * <li>自动设置应用名称为当前应用</li>
     * <li>校验配置键在应用内的唯一性</li>
     * <li>保存配置基本信息</li>
     * <li>如果存在选项则批量保存配置选项</li>
     * <li>记录操作日志</li>
     * <li>返回完整的配置实体</li>
     * </ol>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限，确保只有开发人员可以创建配置。
     *
     * <h3>异常情况：</h3>
     * <ul>
     * <li>配置键已存在：抛出业务异常</li>
     * <li>参数校验失败：返回400错误</li>
     * <li>权限不足：返回403错误</li>
     * </ul>
     *
     * @param source 配置保存DTO，包含配置基本信息和选项，必须通过参数校验
     * @return 保存后的完整配置实体，包含生成的ID和创建时间
     *
     * @throws BusinessException 当配置键已存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @see ConfigSaveDTO 保存参数详细说明
     * @since 1.0.0
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PostMapping("/manager")
    @Operation(summary = "新增配置信息", description = "创建新的系统配置项，支持配置选项，需要开发者权限")
    public ConfigEntity save(@RequestBody @Valid ConfigSaveDTO source) {
        // 自动设置应用名称，实现配置隔离
        source.setApplication(application);
        return configService.saveConfigWithOptions(source);
    }

    /**
     * 更新配置信息
     *
     * <p>根据配置ID更新配置的基本信息和选项，支持增量更新，只更新提供的字段。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>支持增量更新，null字段不会更新</li>
     * <li>配置选项全量替换策略</li>
     * <li>自动记录操作日志</li>
     * <li>事务保证数据一致性</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>验证配置ID存在性</li>
     * <li>增量更新配置基本信息</li>
     * <li>如果提供了选项，删除原有选项并创建新选项</li>
     * <li>记录操作日志</li>
     * <li>返回更新后的完整配置实体</li>
     * </ol>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @param id 配置ID，路径参数，不能为空
     * @param updateDTO 配置更新DTO，支持增量更新，通过参数校验
     * @return 更新后的完整配置实体
     *
     * @throws EntityNotFoundException 当配置不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @see ConfigUpdateDTO 更新参数详细说明
     * @since 1.0.0
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/manager/{id}")
    @Operation(summary = "更新配置信息", description = "支持增量更新配置基本信息和选项，需要开发者权限")
    public ConfigEntity updateConfig(
            @Parameter(description = "配置ID，不能为空", required = true) @PathVariable String id,
            @Parameter(description = "配置更新信息", required = true) @RequestBody @Valid ConfigUpdateDTO updateDTO) {
        updateDTO.setId(id);
        return configService.updateConfigWithOptions(updateDTO);
    }

    /**
     * 删除配置信息
     *
     * <p>根据配置ID删除配置及其所有相关选项，操作不可逆。
     *
     * <h3>删除逻辑：</h3>
     * <ol>
     * <li>验证配置存在性</li>
     * <li>记录删除操作日志</li>
     * <li>删除配置主体信息</li>
     * <li>批量删除所有配置选项</li>
     * </ol>
     *
     * <h3>注意事项：</h3>
     * <ul>
     * <li>删除操作不可逆，请谨慎使用</li>
     * <li>会级联删除所有配置选项</li>
     * <li>删除后配置立即失效</li>
     * </ul>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @param id 配置ID，不能为空
     *
     * @throws EntityNotFoundException 当配置不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @since 1.0.0
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @DeleteMapping("/manager/{id}")
    @Operation(summary = "删除配置信息", description = "根据ID删除配置及其所有选项，操作不可逆，需要开发者权限")
    public void delete(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        ConfigEntity target = configService.getById(id);
        LogContext.setDetail("DELETE: " + target.getKey());
        configService.removeById(id);
        // 级联删除配置选项
        if (CollectionUtils.isNotEmpty(target.getOptions())) {
            Set<String> ids =
                    target.getOptions().stream().map(ConfigOptionEntity::getId).collect(Collectors.toSet());
            configOptionService.removeByIds(ids);
        }
    }

    /**
     * 应用配置刷新
     *
     * <p>触发系统配置的动态刷新，使配置变更立即生效，无需重启应用。
     *
     * <h3>刷新机制：</h3>
     * <ul>
     * <li>清空本地配置缓存</li>
     * <li>重新加载数据库配置</li>
     * <li>通知相关组件配置变更</li>
     * <li>更新Spring容器中的配置Bean</li>
     * </ul>
     *
     * <h3>适用场景：</h3>
     * <ul>
     * <li>配置修改后需要立即生效</li>
     * <li>系统运维期间的配置调整</li>
     * <li>应用配置的热更新</li>
     * </ul>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @since 1.0.0
     */
    @OperationLog
    @PatchMapping("/manager/apply")
    @Operation(summary = "应用配置刷新", description = "触发系统配置动态刷新，使配置变更立即生效")
    public void apply() {
        contextRefresher.apply();
    }

    /**
     * 根据ID查询配置详情
     *
     * <p>获取指定配置的完整信息，包括基本信息和所有配置选项。
     *
     * <h3>返回内容：</h3>
     * <ul>
     * <li>配置基本信息（键、值、名称、描述等）</li>
     * <li>配置所有选项列表</li>
     * <li>配置元数据信息</li>
     * </ul>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @param id 配置ID，不能为空
     * @return 配置完整实体信息
     *
     * @throws EntityNotFoundException 当配置不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @since 1.0.0
     */
    @SaCheckRole("ROLE_DEV")
    @GetMapping("/manager/{id}")
    @Operation(summary = "查询配置详情", description = "根据ID获取配置完整信息，包括选项")
    public ConfigEntity getById(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        return configService.getById(id);
    }

    /**
     * 查询配置选项列表
     *
     * <p>获取指定配置的所有选项信息，用于展示配置的可选值。
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>前端下拉框选项加载</li>
     * <li>配置选项的管理界面</li>
     * <li>配置验证和展示</li>
     * </ul>
     *
     * <h3>权限要求：</h3>
     * <p>需要 ROLE_DEV 角色权限。
     *
     * @param id 配置ID，不能为空
     * @return 配置选项列表，如果没有选项则返回空列表
     *
     * @throws EntityNotFoundException 当配置不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @since 1.0.0
     */
    @SaCheckRole("ROLE_DEV")
    @GetMapping("/manager/{id}/options")
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
     * <h3>查询逻辑：</h3>
     * <ul>
     * <li>如果提供ID列表，按ID精确匹配</li>
     * <li>如果未提供ID列表，返回应用下所有配置</li>
     * <li>结果按配置键名排序</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>系统配置的批量导出</li>
     * <li>配置中心数据同步</li>
     * <li>微服务间配置共享</li>
     * </ul>
     *
     * @param configQueryDTO 批量查询参数，包含应用名称和ID列表
     * @return 配置实体列表，按键名排序
     *
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @see ConfigQueryDTO 批量查询参数说明
     * @since 1.0.0
     */
    @Operation(summary = "批量查询配置列表", description = "根据应用名称和ID列表批量查询配置，用于系统间配置同步")
    @PostMapping("/batch")
    public List<ConfigEntity> batchQueryConfigs(@RequestBody @Valid ConfigQueryDTO configQueryDTO) {
        return configService.batchQueryConfigs(configQueryDTO);
    }

    /**
     * 查询当前系统配置
     *
     * <p>获取当前应用的所有配置信息，主要用于系统运维和监控。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>自动获取当前应用名称</li>
     * <li>返回应用下所有配置</li>
     * <li>包含配置的完整信息</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>系统运维配置检查</li>
     * <li>配置管理界面数据展示</li>
     * <li>应用配置状态监控</li>
     * </ul>
     *
     * @return 当前应用的所有配置列表
     * @since 1.0.0
     */
    @GetMapping("/systems")
    @Operation(summary = "查询当前系统配置", description = "获取当前应用的所有配置信息，用于运维和监控")
    public List<ConfigEntity> getSystemConfig() {
        ConfigQueryDTO configQueryDTO = new ConfigQueryDTO();
        configQueryDTO.setApplication(application);
        return configService.batchQueryConfigs(configQueryDTO);
    }

    /**
     * 批量更新系统配置
     *
     * <p>批量更新多个配置的值，操作完成后自动触发配置刷新，使更改立即生效。
     *
     * <h3>更新逻辑：</h3>
     * <ol>
     * <li>参数校验和权限检查</li>
     * <li>批量执行配置值更新</li>
     * <li>执行配置变更后续处理</li>
     * <li>触发动态配置刷新</li>
     * </ol>
     *
     * <h3>后续处理：</h3>
     * <ul>
     * <li>执行配置变更监听器</li>
     * <li>刷新Spring配置上下文</li>
     * <li>通知相关组件配置更新</li>
     * <li>清理配置缓存</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>运维人员批量调整系统参数</li>
     * <li>系统性能优化配置调整</li>
     * <li>应用环境配置切换</li>
     * </ul>
     *
     * <h3>注意事项：</h3>
     * <ul>
     * <li>批量更新操作具有原子性</li>
     * <li>更新失败会回滚所有更改</li>
     * <li>配置立即生效，请谨慎操作</li>
     * </ul>
     *
     * @param batchUpdateDTO 批量更新参数，包含应用名称和配置更新项列表
     *
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws BusinessException 当更新操作失败时抛出
     * @see ConfigBatchUpdateDTO 批量更新参数说明
     * @since 1.0.0
     */
    @PutMapping("/batch")
    @Operation(summary = "批量更新系统配置", description = "批量更新配置值并触发动态刷新，用于运维人员和管理员")
    public void batchUpdateConfigs(@RequestBody @Valid ConfigBatchUpdateDTO batchUpdateDTO) {
        configService.batchUpdateConfigs(batchUpdateDTO);
        // 执行配置变更后续处理
        configChangedService.execute();
        // 触发动态配置刷新
        contextRefresher.doRefresh();
    }
}
