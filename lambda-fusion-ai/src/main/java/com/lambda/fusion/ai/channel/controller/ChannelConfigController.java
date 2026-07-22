package com.lambda.fusion.ai.channel.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.channel.model.ChannelConfigPage;
import com.lambda.fusion.ai.channel.model.CreateChannelConfig;
import com.lambda.fusion.ai.channel.model.UpdateChannelConfig;
import com.lambda.fusion.ai.channel.model.entity.ChannelConfigEntity;
import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole("ROLE_DEV")
@Tag(name = "通道路由配置管理")
@RestController
@RequestMapping("/v1/ai/channel-configs")
@RequiredArgsConstructor
public class ChannelConfigController {

    private final ChannelConfigService channelConfigService;

    @Operation(summary = "分页查询通道路由配置")
    @GetMapping("/page")
    public Page<ChannelConfigEntity> page(@Valid ChannelConfigPage query) {
        return channelConfigService.page(query);
    }

    @Operation(summary = "查询通道路由配置详情")
    @GetMapping("/{id}")
    public ChannelConfigEntity get(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        return channelConfigService.get(id);
    }

    @Operation(summary = "按 channelId 查询通道路由配置")
    @GetMapping("/by-channel/{channelId}")
    public ChannelConfigEntity getByChannelId(
            @Parameter(description = "通道标识", required = true) @PathVariable String channelId) {
        return channelConfigService.getByChannelId(channelId);
    }

    @OperationLog
    @Operation(summary = "新增通道路由配置")
    @PostMapping
    public ChannelConfigEntity create(@RequestBody @Valid CreateChannelConfig dto) {
        return channelConfigService.create(dto);
    }

    @OperationLog
    @Operation(summary = "更新通道路由配置")
    @PutMapping("/{id}")
    public void update(
            @Parameter(description = "配置ID", required = true) @PathVariable String id,
            @RequestBody @Valid UpdateChannelConfig dto) {
        channelConfigService.update(id, dto);
    }

    @OperationLog
    @Operation(summary = "删除通道路由配置")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "配置ID", required = true) @PathVariable String id) {
        channelConfigService.delete(id);
    }
}
