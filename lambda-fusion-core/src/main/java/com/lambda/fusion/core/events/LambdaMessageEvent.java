package com.lambda.fusion.core.events;

import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.core.Constants;
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

    @Schema(description = Constants.SCHEMA_CODE)
    private String code;

    @Schema(description = Constants.SCHEMA_SENDER)
    private String sender;

    @Schema(description = Constants.SCHEMA_CONTENT)
    private String content;

    @Schema(description = Constants.SCHEMA_TRIGGER_TIME)
    private Date triggerTime;

    @Schema(description = Constants.SCHEMA_BUSINESS_KEY)
    private String businessKey;

    @Schema(description = Constants.SCHEMA_INPUTS)
    private Map<String, String> inputs = new HashMap<>(Constants.DEFAULT_HASH_MAP_CAPACITY);

    private Receiver receiver;

    public void setTarget(Receiver receiver) {
        Assert.isFalse(
                receiver == null || receiver.getType() == null || CollectionUtils.isEmpty(receiver.getTarget()),
                Constants.MSG_MESSAGE_EVENT_NO_RECEIVER);
        this.receiver = receiver;
    }

    public LambdaMessageEvent putInputs(String key, String val) {
        inputs.put(key, val);
        return this;
    }

    @Data
    @SuppressFBWarnings({"EI_EXPOSE_REP"})
    public static class Receiver {

        @Schema(description = Constants.SCHEMA_RECEIVER_TYPE)
        private Integer type;

        @Schema(description = Constants.SCHEMA_RECEIVER_TARGET)
        private List<String> target;
    }
}
