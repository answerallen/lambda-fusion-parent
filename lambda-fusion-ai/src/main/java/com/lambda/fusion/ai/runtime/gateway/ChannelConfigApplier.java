package com.lambda.fusion.ai.runtime.gateway;

import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 把持久化的通道路由配置下发到运行中的 {@link Channel}：启动时全量下发，admin CRUD 后按 channelId 下发。
 *
 * <p>Gateway 未启用时 {@link ChannelManager} 缺省 -> 下发 no-op；channel 未注册时遍历空集 -> no-op。
 * 全程 try/catch，下发失败不影响 admin CRUD 返回。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelConfigApplier {

    private final ObjectProvider<ChannelManager> channelManagerProvider;
    private final ObjectProvider<ChannelConfigService> channelConfigServiceProvider;

    /**
     * 启动时把 DB 配置推给所有已注册 channel。
     */
    public void applyAll() {
        ChannelManager cm = channelManagerProvider.getIfAvailable();
        if (cm == null) {
            return;
        }
        for (Channel ch : cm.getAllChannels()) {
            applyOne(ch);
        }
    }

    /**
     * admin CRUD 后按 channelId 下发到对应 channel（若已注册）。
     */
    public void applyChannel(String channelId) {
        ChannelManager cm = channelManagerProvider.getIfAvailable();
        if (cm == null) {
            return;
        }
        cm.getChannel(channelId).ifPresent(this::applyOne);
    }

    private void applyOne(Channel channel) {
        ChannelConfigService service = channelConfigServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            ChannelConfig config = service.resolve(channel.channelId());
            if (config == null) {
                return; // 无持久化配置，保留 channel 自带默认
            }
            channel.applyRoutingConfig(config);
            log.debug("已下发通道路由配置: channelId={}", channel.channelId());
        } catch (Exception e) {
            log.warn("下发通道路由配置失败(channelId={}): {}", channel.channelId(), e.getMessage());
        }
    }
}
