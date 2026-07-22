package com.lambda.fusion.ai.chat.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 主动出站消息请求。{@code sessionId}（回推会话最近入站通道）或 {@code channelId + to}（显式通道投递）二选一。
 *
 * @author Jin
 */
@Data
@Schema(description = "主动出站消息请求")
public class SendOutbound {

    @Schema(description = "目标会话ID（与 channelId 二选一）：推送到该会话最近一次入站记录的通道地址")
    private String sessionId;

    @Schema(description = "目标通道ID（与 sessionId 二选一）：显式通道投递")
    private String channelId;

    @Schema(description = "通道内投递地址，如 dingtalk:GROUP:cidXXX（channelId 模式必填）")
    private String to;

    @Schema(description = "要推送的消息文本列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> messages;
}
