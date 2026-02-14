package com.lambda.fusion.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.config.model.ConfigOption;
import com.lambda.fusion.config.model.ConfigOptionEntity;
import com.lambda.fusion.config.service.ConfigOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
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
 * @see ConfigOptionService 配置选项服务接口
 * @see ConfigOptionEntity 配置选项实体类
 * @see ConfigOption 配置选项视图对象
 * @since 1.0.0
 */
@Tag(name = "系统配置管理")
@RestController
@AllArgsConstructor
@RequestMapping("/config/options")
public class ConfigOptionController {

    private final ConfigOptionService configOptionService;

    /**
     * 更新配置选项信息
     *
     * <p>根据配置选项ID更新选项的详细信息，支持增量更新，保护关键字段不被修改。
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
