package com.lambda.fusion.configs.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.bean.BeanUtil;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.domain.vo.ConfigOptionVO;
import com.lambda.fusion.configs.service.ConfigOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/configs/options")
public class ConfigsOptionController {

    @Autowired
    private ConfigOptionService configOptionService;

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @PutMapping("/{id}")
    @Operation(summary = "根据编号更新选项信息")
    public ConfigOptionEntity update(
            @Parameter(description = "配置编号", required = true) @PathVariable String id,
            @Parameter(description = "配置信息", required = true) @RequestBody @Valid ConfigOptionVO source) {
        ConfigOptionEntity target = configOptionService.getById(id);
        BeanUtil.copyProperties(source, target);
        // 不修改PROPERTY_ID和APPLICATION的值
        target.setPid(null);
        target.setApplication(null);
        configOptionService.updateById(target);
        LogContext.setDetail("UPDATE: " + target.getName() + "=" + target.getValue());
        return target;
    }

    @OperationLog
    @SaCheckRole("ROLE_DEV")
    @DeleteMapping("/{id}")
    @Operation(summary = "根据编号删除选项信息")
    public void delete(@Parameter(description = "编号", required = true) @PathVariable String id) {
        ConfigOptionEntity target = configOptionService.getById(id);
        LogContext.setDetail("DELETE: " + target.getName());
        configOptionService.removeById(id);
    }
}
