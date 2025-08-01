package com.lambda.fusion.core.events;

import com.lambda.cloud.core.utils.Assert;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.util.CollectionUtils;

/**
 * @author Jin
 */
@Data
@SuppressFBWarnings({"EI_EXPOSE_REP"})
public class LambdaMessageEvent {

    @Schema(description = "CODE")
    private String code;

    @Schema(description = "发送人")
    private String sender;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "触发时间")
    private Date triggerTime;

    @Schema(description = "业务唯一标识")
    private String businessKey;

    @Schema(description = "输入参数")
    private Map<String, String> inputs = new HashMap<>(8);

    private Receiver receiver;

    public void setTarget(Receiver receiver) {
        Assert.isFalse(
                receiver == null || receiver.getType() == null || CollectionUtils.isEmpty(receiver.getTarget()),
                "fx.message.client.event.not.receiver");
        this.receiver = receiver;
    }

    public LambdaMessageEvent putInputs(String key, String val) {
        inputs.put(key, val);
        return this;
    }

    @Data
    @SuppressFBWarnings({"EI_EXPOSE_REP"})
    public static class Receiver {

        @Schema(description = "通知对象类型 1-角色 2-用户 4-无 5-所有用户")
        private Integer type;

        @Schema(description = "通知对象参数")
        private List<String> target;
    }
}
