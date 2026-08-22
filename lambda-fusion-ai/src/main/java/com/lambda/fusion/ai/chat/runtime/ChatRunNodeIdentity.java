package com.lambda.fusion.ai.chat.runtime;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话 Run 执行节点标识：以「应用名 + 进程启动时自动生成的 boot UUID」唯一标识本节点。
 * 仅用于标记 Run 由哪个节点执行、以及失效扫描时的归属判断；不携带所有权/租约语义，
 * 不参与 fencing 或接管。
 *
 * @author Jin
 */
@Component
public class ChatRunNodeIdentity {

    private final String instanceId;

    /**
     * 创建节点标识。
     *
     * @param applicationName 应用名（{@code spring.application.name}），缺省按 {@code ai} 处理
     */
    public ChatRunNodeIdentity(@Value("${spring.application.name:ai}") String applicationName) {
        this.instanceId = applicationName + "-" + UUID.randomUUID();
    }

    /**
     * 本节点的唯一执行标识。
     *
     * @return 节点实例标识
     */
    public String instanceId() {
        return instanceId;
    }
}
