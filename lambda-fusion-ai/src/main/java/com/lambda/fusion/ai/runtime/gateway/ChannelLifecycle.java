package com.lambda.fusion.ai.runtime.gateway;

import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.channel.Channel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * 外部通道生命周期：启动时将所有 {@link Channel} Bean 注册到 {@link ChannelManager} 并拉起，停止时关闭。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelLifecycle implements SmartLifecycle {

    private final ChannelManager channelManager;
    private final HarnessGateway gateway;
    private final List<Channel> channels;
    private final ChannelConfigApplier channelConfigApplier;

    private volatile boolean running = false;

    @Override
    public void start() {
        for (Channel ch : channels) {
            channelManager.register(ch);
        }
        channelManager.initAll(gateway);
        channelManager.startAll();
        channelConfigApplier.applyAll();
        running = true;
        log.info("AI Gateway 就绪，已启动通道: {}", channelManager.channelIds());
    }

    @Override
    public void stop() {
        channelManager.stopAll();
        running = false;
        log.info("AI Gateway 通道已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return SmartLifecycle.super.isAutoStartup();
    }

    @Override
    public int getPhase() {
        // 晚于绝大多数 SmartLifecycle bean（低于此值），确保数据源/服务等依赖已就绪
        return Integer.MAX_VALUE - 100;
    }
}
