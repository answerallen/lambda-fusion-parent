package com.lambda.fusion.configs.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.configs.core.DatabaseBasedPropertySourceLocator;
import com.lambda.fusion.configs.core.DatabaseContextRefresher;
import com.lambda.fusion.configs.domain.dto.Parameters;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.domain.vo.ConfigBatchQueryVO;
import com.lambda.fusion.configs.domain.vo.ConfigVO;
import com.lambda.fusion.configs.service.ConfigChangedService;
import com.lambda.fusion.configs.service.ConfigOptionService;
import com.lambda.fusion.configs.service.ConfigService;
import com.lambda.fusion.core.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/configs")
public class ConfigsController {

    @Value("${spring.application.name}")
    private String application;

    @Autowired
    private DatabaseContextRefresher contextRefresher;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ConfigOptionService configOptionService;

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private ConfigChangedService configChangedService;

    @Autowired
    private DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator;

    @Operation(
            summary = "分页查询所有数据列表",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20"))
            })
    @GetMapping({"/manager/page/{number:\\d+}", "/manager/page/{number:\\d+}/size/{size:\\d+}"})
    public Page<ConfigEntity> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid Parameters parameters) {
        return configService.page(new Page<>(), parameters);
    }

    @Operation(
            summary = "查询所有数据列表",
            parameters = {
                @Parameter(name = "key", description = "配置信息键,支持右侧模糊查询", in = ParameterIn.QUERY),
                @Parameter(name = "ids", description = "查询的id列表，以“,”分割", in = ParameterIn.QUERY),
                @Parameter(name = "keys", description = "查询的key列表，以“,”分割", in = ParameterIn.QUERY)
            })
    @GetMapping
    public List<ConfigEntity> list(
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "ids", required = false) String ids,
            @RequestParam(name = "keys", required = false) String keys) {
        LambdaQueryWrapper<ConfigEntity> queryWrapper = Wrappers.lambdaQuery(ConfigEntity.class);
        if (StringUtils.isNotBlank(key)) {
            queryWrapper.likeRight(ConfigEntity::getKey, key);
        }
        if (StringUtils.isNotBlank(ids)) {
            queryWrapper.in(ConfigEntity::getId, Arrays.asList(ids.split(Constants.DELIMITER)));
        }
        if (StringUtils.isNotBlank(keys)) {
            queryWrapper.in(ConfigEntity::getKey, Arrays.asList(keys.split(Constants.DELIMITER)));
        }
        return configService.list(queryWrapper);
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PostMapping("/manager")
    @Operation(summary = "新增配置信息")
    public ConfigEntity save(@Parameter(description = "配置信息", required = true) @RequestBody ConfigVO source) {
        return configService.saveConfig(application, source);
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/manager/{id}")
    @Operation(summary = "根据编号更新配置信息")
    public ConfigEntity update(
            @Parameter(description = "配置编号", required = true) @PathVariable String id,
            @Parameter(description = "配置信息", required = true) @RequestBody ConfigVO source) {
        ConfigEntity target = configService.getById(id);
        BeanUtil.copyProperties(source, target);
        target.setId(id);
        configService.updateById(target);
        LogContext.setDetail("UPDATE: " + target.getKey() + "=" + target.getValue());
        return target;
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @DeleteMapping("/manager/{id}")
    @Operation(summary = "根据编号删除配置信息")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ConfigEntity target = configService.getById(id);
        LogContext.setDetail("DELETE: " + target.getKey());
        configService.removeById(id);
        if (CollectionUtils.isNotEmpty(target.getOptions())) {
            Set<String> ids =
                    target.getOptions().stream().map(ConfigOptionEntity::getId).collect(Collectors.toSet());
            configOptionService.removeByIds(ids);
        }
    }

    @OperationLog
    @PatchMapping("/manager/apply")
    @Operation(summary = "应用所有配置信息")
    public void apply() {
        contextRefresher.apply();
    }

    @SaCheckRole("ROLE_DEV")
    @GetMapping("/manager/{id}")
    @Operation(summary = "根据编号查询配置信息")
    public ConfigEntity getById(@Parameter(description = "配置编号", required = true) @PathVariable String id) {
        return configService.getById(id);
    }

    @SaCheckRole("ROLE_DEV")
    @GetMapping("/manager/{id}/options")
    @Operation(summary = "根据编号查询配置的选项列表")
    public List<ConfigOptionEntity> getOptionsByConfigId(
            @Parameter(description = "配置编号", required = true) @PathVariable String id) {
        ConfigEntity configEntity = configService.getById(id);
        return configEntity.getOptions();
    }

    @Operation(summary = "根据多个编号查询配置列表")
    @PostMapping("/manager/byids")
    public List<ConfigEntity> getMultipleConfigsByIds(@RequestBody ConfigBatchQueryVO query) {
        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("ids", query.getArrays());
        return configService.queryConfigsByConditions(parameters);
    }

    @GetMapping("/systems")
    @Operation(summary = "查询系统配置信息", description = "用于运维人员或管理员")
    public List<ConfigEntity> getSystemConfig() {
        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("application", application);
        return configService.queryConfigsByConditions(parameters);
    }

    @PutMapping
    @Operation(summary = "更新系统配置信息", description = "用于运维人员或管理员")
    public void updateSystemConfig(@RequestBody List<ConfigEntity> updated) {
        if (CollectionUtils.isNotEmpty(updated)) {
            configService.updateBatchByApplication(null, updated);
            configChangedService.execute();
            contextRefresher.doRefresh();
        }
    }
}
