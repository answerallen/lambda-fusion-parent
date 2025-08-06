package com.lambda.fusion.configs.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.configs.core.DatabaseContextRefresher;
import com.lambda.fusion.configs.domain.dto.*;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.service.ConfigChangedService;
import com.lambda.fusion.configs.service.ConfigOptionService;
import com.lambda.fusion.configs.service.ConfigService;
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

@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
public class ConfigsController {

    @Value("${spring.application.name}")
    private String application;

    private final DatabaseContextRefresher contextRefresher;
    private final ConfigService configService;
    private final ConfigOptionService configOptionService;
    private final ConfigChangedService configChangedService;

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
            @Valid ConfigPageQueryDTO configPageQueryDTO) {
        return configService.pageConfigs(new Page<>(number, size), configPageQueryDTO);
    }

    @Operation(summary = "查询配置列表", description = "支持按键名、ID列表、键列表进行查询")
    @GetMapping
    public List<ConfigEntity> listConfigs(@Valid ConfigListQueryDTO queryDTO) {
        // 处理逗号分隔的字符串参数
        if (queryDTO.getIds() == null && StringUtils.isNotBlank(queryDTO.getIdsString())) {
            queryDTO.setIds(Arrays.asList(queryDTO.getIdsString().split(",")));
        }
        if (queryDTO.getKeys() == null && StringUtils.isNotBlank(queryDTO.getKeysString())) {
            queryDTO.setKeys(Arrays.asList(queryDTO.getKeysString().split(",")));
        }
        return configService.listConfigs(queryDTO);
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PostMapping("/manager")
    @Operation(summary = "新增配置信息")
    public ConfigEntity save(@RequestBody ConfigSaveDTO source) {
        source.setApplication(application);
        return configService.saveConfigWithOptions(source);
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/manager/{id}")
    @Operation(summary = "根据编号更新配置信息")
    public ConfigEntity updateConfig(
            @Parameter(description = "配置编号", required = true) @PathVariable String id,
            @Parameter(description = "配置信息", required = true) @RequestBody @Valid ConfigUpdateDTO updateDTO) {
        updateDTO.setId(id);
        return configService.updateConfigWithOptions(updateDTO);
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

    @Operation(summary = "根据多个编号批量查询配置列表")
    @PostMapping("/batch")
    public List<ConfigEntity> batchQueryConfigs(@RequestBody @Valid ConfigQueryDTO configQueryDTO) {
        return configService.batchQueryConfigs(configQueryDTO);
    }

    @GetMapping("/systems")
    @Operation(summary = "查询系统配置信息", description = "用于运维人员或管理员")
    public List<ConfigEntity> getSystemConfig() {
        ConfigQueryDTO configQueryDTO = new ConfigQueryDTO();
        configQueryDTO.setApplication(application);
        return configService.batchQueryConfigs(configQueryDTO);
    }

    @PutMapping("/batch")
    @Operation(summary = "批量更新系统配置信息", description = "用于运维人员或管理员")
    public void batchUpdateConfigs(@RequestBody @Valid ConfigBatchUpdateDTO batchUpdateDTO) {
        configService.batchUpdateConfigs(batchUpdateDTO);
        configChangedService.execute();
        contextRefresher.doRefresh();
    }
}
