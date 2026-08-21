package com.lambda.fusion.ai.chat.runtime;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话 Run 集群执行节点标识：以「应用名 + 进程启动时自动生成的 boot UUID」唯一标识本节点，
 * 供 ChatRun 的 owner/lease fencing 使用。boot UUID 每次进程启动重新生成，不作为人工配置项。
 *
 * @author Jin
 */
@Component
public class ChatRunOwner {

    private final String instanceId;

    /**
     * 创建节点标识。
     *
     * @param applicationName 应用名（{@code spring.application.name}），缺省按 {@code ai} 处理
     */
    public ChatRunOwner(@Value("${spring.application.name:ai}") String applicationName) {
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
