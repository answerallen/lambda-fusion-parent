package com.lambda.fusion.ai.runtime.gateway;

import com.lambda.fusion.ai.channel.model.ChannelDefinition;
import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelFactory;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * DB 驱动的渠道启动器：应用就绪时按 {@code ai_channel_config} 启用行构造 {@link Channel} 注册进
 * {@link ChannelManager}；admin CRUD 后按 channelId 重建。
 *
 * <p>构造委托给按 type 命名的 {@link ChannelFactory} Bean（钉钉/飞书/企微）。无匹配工厂（扩展未引入）
 * -> 单条 warn 跳过；单条构造失败 -> 隔离，不阻塞其余与启动。仅在 gateway 启用时装配（见
 * {@code AiConfigure.GatewayConfiguration}），故直接注入 ChannelManager/HarnessGateway。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChannelBootstrap {

    private final ChannelManager channelManager;
    private final HarnessGateway gateway;
    private final ChannelConfigService channelConfigService;
    private final Map<String, ChannelFactory> factories;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapAll() {
        List<ChannelDefinition> defs = channelConfigService.loadEnabledDefinitions();
        if (defs.isEmpty()) {
            return;
        }
        log.info("启动 DB 渠道: {} 个", defs.size());
        for (ChannelDefinition def : defs) {
            build(def);
        }
    }

    // 先停旧 channel，再按当前 DB 状态重建；禁用/删除则只停不建
    public void rebuild(String channelId) {
        stopAndUnregister(channelId);
        ChannelDefinition def = channelConfigService.resolveDefinition(channelId);
        if (def == null) {
            return;
        }
        build(def);
    }

    private void build(ChannelDefinition def) {
        ChannelFactory factory = factories.get(def.type());
        if (factory == null) {
            log.warn("渠道 {} 的适配器类型 {} 无可用工厂(扩展未引入)，跳过", def.channelId(), def.type());
            return;
        }
        try {
            Channel channel = factory.create(def.channelId(), def.routing(), def.properties());
            channelManager.register(channel);
            channel.init(gateway);
            channel.start();
            log.info("渠道已启动: channelId={}, type={}", def.channelId(), def.type());
        } catch (Exception e) {
            log.warn("渠道 {} 构造失败: {}", def.channelId(), e.getMessage(), e);
        }
    }

    private void stopAndUnregister(String channelId) {
        channelManager.getChannel(channelId).ifPresent(old -> {
            try {
                old.stop();
            } catch (Exception e) {
                log.warn("渠道 {} 停止失败: {}", channelId, e.getMessage());
            }
            channelManager.unregister(channelId);
        });
    }
}
