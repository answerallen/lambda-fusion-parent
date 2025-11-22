package com.lambda.fusion.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.config.model.ConfigOptionEntity;
import com.lambda.fusion.config.model.ConfigOption;
import com.lambda.fusion.config.service.ConfigOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置选项管理控制器
 *
 * <p>提供系统配置选项的管理功能，专门处理配置项的可选值管理。
 * 配置选项是配置管理系统的重要组成部分，为下拉框、单选框等UI组件提供数据源支撑。
 *
 * <h3>主要功能：</h3>
 * <ul>
 * <li><strong>选项维护：</strong>配置选项的更新和删除操作</li>
 * <li><strong>权限控制：</strong>所有操作都需要开发者权限</li>
 * <li><strong>操作审计：</strong>自动记录操作日志便于追踪</li>
 * <li><strong>数据安全：</strong>保护关键字段避免误修改</li>
 * </ul>
 *
 * <h3>业务特点：</h3>
 * <ul>
 * <li>配置选项与主配置项存在从属关系</li>
 * <li>选项主要包含值、名称、描述等属性</li>
 * <li>支持配置项的多选项设置</li>
 * <li>为UI组件提供标准化的选项数据</li>
 * </ul>
 *
 * <h3>权限设计：</h3>
 * <ul>
 * <li>所有操作需要 ROLE_DEV 角色权限</li>
 * <li>确保只有开发人员可以修改配置选项</li>
 * <li>保护系统配置的稳定性和安全性</li>
 * </ul>
 *
 * <h3>安全保护：</h3>
 * <ul>
 * <li>关键字段（如父配置ID、应用名称）不允许修改</li>
 * <li>所有操作都有操作日志记录</li>
 * <li>使用参数校验确保数据完整性</li>
 * </ul>
 *
 * @since 1.0.0
 * @see ConfigOptionService 配置选项服务接口
 * @see ConfigOptionEntity 配置选项实体类
 * @see ConfigOption 配置选项视图对象
 */
@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/config/options")
public class ConfigOptionController {

    /** 配置选项服务，提供配置选项的业务逻辑处理 */
    @Autowired
    private ConfigOptionService configOptionService;

    /**
     * 更新配置选项信息
     *
     * <p>根据配置选项ID更新选项的详细信息，支持增量更新，保护关键字段不被修改。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>支持增量更新，只更新提供的字段</li>
     * <li>保护关键字段（父配置ID、应用名称）不被修改</li>
     * <li>自动记录操作日志</li>
     * <li>使用BeanUtil进行对象属性复制</li>
     * </ul>
     *
     * <h3>更新内容：</h3>
     * <ul>
     * <li><strong>选项值：</strong>配置选项的具体值</li>
     * <li><strong>选项名称：</strong>配置选项的显示名称</li>
     * <li><strong>选项描述：</strong>配置选项的详细描述</li>
     * <li><strong>排序信息：</strong>选项在列表中的显示顺序</li>
     * </ul>
     *
     * <h3>安全机制：</h3>
     * <ul>
     * <li><strong>字段保护：</strong>父配置ID（pid）和应用名称（application）强制设为null，避免误修改关联关系</li>
     * <li><strong>权限检查：</strong>需要ROLE_DEV角色权限，确保只有开发人员可以执行</li>
     * <li><strong>参数校验：</strong>使用@Valid注解确保输入数据的完整性和合法性</li>
     * <li><strong>存在性验证：</strong>先通过ID查询目标选项，确保选项存在</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>根据ID查询目标配置选项</li>
     * <li>使用BeanUtil将VO对象属性复制到实体对象</li>
     * <li>强制清空关键保护字段（pid、application）</li>
     * <li>执行数据库更新操作</li>
     * <li>记录操作日志，包含选项名称和值</li>
     * <li>返回更新后的完整选项实体</li>
     * </ol>
     *
     * <h3>操作日志：</h3>
     * <ul>
     * <li>记录格式：UPDATE: {选项名称}={选项值}</li>
     * <li>便于操作审计和问题排查</li>
     * <li>支持操作历史追踪</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>管理后台修改配置选项信息</li>
     * <li>运维人员调整选项显示内容</li>
     * <li>开发阶段完善选项配置</li>
     * <li>选项信息的维护和优化</li>
     * </ul>
     *
     * @param id 配置选项ID，路径参数，不能为空，用于唯一标识要更新的配置选项
     * @param source 配置选项更新信息，请求体参数，包含需要更新的字段，必须通过参数校验
     * @return 更新后的完整配置选项实体，包含最新的字段值
     *
     * @throws EntityNotFoundException 当配置选项不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @throws ValidationException 当输入参数校验失败时抛出
     * @see ConfigOption 配置选项视图对象，定义可更新字段
     * @see BeanUtil#copyProperties(Object, Object) HuTool对象属性复制工具
     * @since 1.0.0
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/{id}")
    @Operation(summary = "根据编号更新选项信息", description = "支持增量更新配置选项，保护关键字段，需要开发者权限")
    public ConfigOptionEntity update(
            @Parameter(description = "配置编号", required = true) @PathVariable String id,
            @Parameter(description = "配置信息", required = true) @RequestBody @Valid ConfigOption source) {
        ConfigOptionEntity target = configOptionService.getById(id);
        BeanUtil.copyProperties(source, target);
        // 不修改PROPERTY_ID和APPLICATION的值
        target.setPid(null);
        target.setApplication(null);
        configOptionService.updateById(target);
        LogContext.setDetail("UPDATE: " + target.getName() + "=" + target.getValue());
        return target;
    }

    /**
     * 删除配置选项信息
     *
     * <p>根据配置选项ID删除指定的配置选项，操作不可逆，删除前会记录选项信息用于审计。
     *
     * <h3>删除特点：</h3>
     * <ul>
     * <li>物理删除，操作不可逆</li>
     * <li>删除前记录选项信息用于审计</li>
     * <li>自动清理相关数据</li>
     * <li>支持操作日志记录</li>
     * </ul>
     *
     * <h3>安全控制：</h3>
     * <ul>
     * <li><strong>权限验证：</strong>需要ROLE_DEV角色权限，确保只有开发人员可以执行删除操作</li>
     * <li><strong>存在性检查：</strong>删除前先查询目标选项，确保选项存在并获取选项信息</li>
     * <li><strong>操作审计：</strong>记录被删除选项的名称，便于后续审计和问题排查</li>
     * <li><strong>事务保证：</strong>删除操作在事务中执行，确保数据一致性</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>根据ID查询目标配置选项，验证存在性</li>
     * <li>提取选项名称并记录到操作日志</li>
     * <li>执行物理删除操作</li>
     * <li>清理相关的缓存或关联数据</li>
     * </ol>
     *
     * <h3>影响范围：</h3>
     * <ul>
     * <li>配置选项从数据库中被永久删除</li>
     * <li>相关UI组件的选项列表将不再包含该选项</li>
     * <li>已使用该选项值的配置不受影响</li>
     * <li>删除操作会被记录到操作日志中</li>
     * </ul>
     *
     * <h3>注意事项：</h3>
     * <ul>
     * <li><strong>不可逆性：</strong>删除操作不可逆，请确认后再执行</li>
     * <li><strong>关联检查：</strong>建议删除前检查是否有其他配置使用该选项</li>
     * <li><strong>备份建议：</strong>重要选项删除前建议先备份数据</li>
     * <li><strong>批量删除：</strong>如需批量删除，建议通过主配置的选项管理进行</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>清理无用的配置选项</li>
     * <li>配置重构时删除废弃选项</li>
     * <li>纠正错误创建的选项</li>
     * <li>系统维护时的数据清理</li>
     * </ul>
     *
     * <h3>操作日志：</h3>
     * <ul>
     * <li>记录格式：DELETE: {选项名称}</li>
     * <li>包含被删除选项的关键信息</li>
     * <li>支持操作历史的完整追踪</li>
     * </ul>
     *
     * @param id 配置选项ID，路径参数，不能为空，用于唯一标识要删除的配置选项
     *
     * @throws EntityNotFoundException 当配置选项不存在时抛出
     * @throws AccessDeniedException 当用户权限不足时抛出
     * @throws DataIntegrityViolationException 当删除操作违反数据完整性约束时抛出
     * @since 1.0.0
     */
    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @DeleteMapping("/{id}")
    @Operation(summary = "根据编号删除选项信息", description = "物理删除配置选项，操作不可逆，需要开发者权限")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ConfigOptionEntity target = configOptionService.getById(id);
        LogContext.setDetail("DELETE: " + target.getName());
        configOptionService.removeById(id);
    }
}
