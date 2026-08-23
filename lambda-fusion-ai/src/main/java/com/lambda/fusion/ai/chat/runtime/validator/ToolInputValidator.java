package com.lambda.fusion.ai.chat.runtime.validator;

import com.lambda.fusion.ai.chat.model.SubmitToolInput;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.agui.InterruptFactory;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * HITL 挂起输入一致性校验：以「最后一条助手消息中无结果且非 ASKING 的工具调用块」为共同基准，
 * 校验快照待输入投影、用户输入与 Agent 状态三方一致，并按 {@code responseSchema} 校验输入值，
 * 最后构造携带用户输入的恢复消息（TOOL 角色、单条多结果块，满足 AgentScope
 * {@code validateAndAddToolResults} 的 ID 匹配与不重复约束）；{@code value} 置空视为取消。
 *
 * @author Jin
 */
@Slf4j
@UtilityClass
public class ToolInputValidator {

    /** 用户取消交互时的结果文本，与官方恢复协议的取消语义对齐。 */
    private static final String CANCELLED_TEXT = "Interrupt cancelled by user";

    /**
     * 校验快照、用户输入与 Agent 状态三方一致，并构造携带用户输入结果的恢复消息。
     * 输入与快照校验通过后才读取 Agent 状态（惰性供应），保持「非法输入不触碰 Agent 状态」的求值顺序。
     *
     * @param run 运行实体
     * @param agentName 构造 TOOL 消息使用的 Agent 名称
     * @param pendingInputs 快照中的待输入投影
     * @param inputs 用户输入回复
     * @param suspendedBlocksSupplier Agent 状态中挂起工具调用块的惰性供应
     * @return 携带用户输入结果的恢复消息
     * @throws AiBusinessException 输入非法或三方工具调用不一致
     */
    public static Msg validateAndBuildMessage(
            ChatRunEntity run,
            String agentName,
            List<ChatRunSnapshot.PendingInput> pendingInputs,
            List<SubmitToolInput.Input> inputs,
            Supplier<List<ToolUseBlock>> suspendedBlocksSupplier) {
        if (inputs == null || inputs.isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "输入回复不能为空");
        }
        Set<String> submittedIds = new HashSet<>();
        for (SubmitToolInput.Input input : inputs) {
            if (StringUtils.isBlank(input.getToolCallId()) || !submittedIds.add(input.getToolCallId())) {
                throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "输入回复必须完整且不能重复: " + input.getToolCallId());
            }
        }
        Map<String, ChatRunSnapshot.PendingInput> pendingById = new LinkedHashMap<>();
        for (ChatRunSnapshot.PendingInput pending : pendingInputs) {
            if (pendingById.put(pending.toolCallId(), pending) != null) {
                throw contextMismatch(run, "快照待输入工具ID重复: " + pending.toolCallId());
            }
        }
        Map<String, ToolUseBlock> blockById = new LinkedHashMap<>();
        for (ToolUseBlock block : suspendedBlocksSupplier.get()) {
            if (blockById.put(block.getId(), block) != null) {
                throw contextMismatch(run, "Agent挂起工具ID重复: " + block.getId());
            }
        }
        if (!pendingById.keySet().equals(submittedIds) || !pendingById.keySet().equals(blockById.keySet())) {
            log.warn(
                    "挂起输入上下文不一致: runId={}, phaseNo={}, snapshotCount={}, inputCount={}, agentSuspendedCount={}",
                    run.getId(),
                    run.getPhaseNo(),
                    pendingById.size(),
                    submittedIds.size(),
                    blockById.size());
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
        }
        List<ContentBlock> results = new ArrayList<>();
        for (SubmitToolInput.Input input : inputs) {
            results.add(buildResultBlock(
                    input, pendingById.get(input.getToolCallId()), blockById.get(input.getToolCallId())));
        }
        return Msg.builder().name(agentName).role(MsgRole.TOOL).content(results).build();
    }

    /** 按交互类型校验输入值并构造结果块；value 置空视为取消。 */
    private static ToolResultBlock buildResultBlock(
            SubmitToolInput.Input input, ChatRunSnapshot.PendingInput pending, ToolUseBlock tool) {
        JsonNode value = input.getValue();
        if (value == null || value.isNull()) {
            return ToolResultBlock.text(CANCELLED_TEXT)
                    .withIdAndName(tool.getId(), tool.getName())
                    .withState(ToolResultState.INTERRUPTED);
        }
        Map<String, Object> property = responseProperty(pending);
        String resultText;
        switch (pending.inputKind() == null ? "" : pending.inputKind()) {
            case InterruptFactory.KIND_SINGLE_CHOICE -> {
                requireTextual(value, input);
                requireEnum(List.of(value.asString()), property, input);
                resultText = value.asString();
            }
            case InterruptFactory.KIND_MULTI_CHOICE -> {
                if (!value.isArray()) {
                    throw invalid("多选值必须为数组: " + input.getToolCallId());
                }
                List<String> values = new ArrayList<>();
                for (JsonNode item : value) {
                    if (item == null || !item.isString()) {
                        throw invalid("多选元素必须为字符串: " + input.getToolCallId());
                    }
                    values.add(item.asString());
                }
                requireEnum(values, property, input);
                requireSize(values.size(), property, input);
                resultText = value.toString();
            }
            default -> {
                requireTextual(value, input);
                Integer maxLength = integer(property.get("maxLength"));
                if (maxLength != null && value.asString().length() > maxLength) {
                    throw invalid("文本长度超过上限" + maxLength + ": " + input.getToolCallId());
                }
                resultText = value.asString();
            }
        }
        return ToolResultBlock.text(resultText)
                .withIdAndName(tool.getId(), tool.getName())
                .withState(ToolResultState.SUCCESS);
    }

    /** 解析 responseSchema 中唯一的恢复值属性定义（choice/choices/text）。 */
    private static Map<String, Object> responseProperty(ChatRunSnapshot.PendingInput pending) {
        String json = pending.responseSchemaJson();
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        Object decoded = JsonUtils.getJsonCodec().fromJson(json, Object.class);
        if (!(decoded instanceof Map<?, ?> schema)) {
            return Map.of();
        }
        Object properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap) || propertyMap.isEmpty()) {
            return Map.of();
        }
        Object first = propertyMap.values().iterator().next();
        if (!(first instanceof Map<?, ?> property)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        property.forEach((key, val) -> result.put(String.valueOf(key), val));
        return result;
    }

    private static void requireTextual(JsonNode value, SubmitToolInput.Input input) {
        if (!value.isString()) {
            throw invalid("输入值必须为字符串: " + input.getToolCallId());
        }
    }

    /** 校验取值都在 schema 的选项枚举内（单选枚举在 property.enum，多选按 JSON Schema 规范在 items.enum）。 */
    private static void requireEnum(List<String> values, Map<String, Object> property, SubmitToolInput.Input input) {
        List<?> options = property.get("enum") instanceof List<?> direct && !direct.isEmpty()
                ? direct
                : property.get("items") instanceof Map<?, ?> items
                                && items.get("enum") instanceof List<?> nested
                                && !nested.isEmpty()
                        ? nested
                        : null;
        if (options == null) {
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (Object option : options) {
            allowed.add(String.valueOf(option));
        }
        for (String value : values) {
            if (!allowed.contains(value)) {
                throw invalid("输入值不在可选项内: " + input.getToolCallId());
            }
        }
    }

    /** 校验多选数量满足 minItems/maxItems。 */
    private static void requireSize(int size, Map<String, Object> property, SubmitToolInput.Input input) {
        Integer minItems = integer(property.get("minItems"));
        Integer maxItems = integer(property.get("maxItems"));
        if (minItems != null && size < minItems) {
            throw invalid("至少选择" + minItems + "项: " + input.getToolCallId());
        }
        if (maxItems != null && size > maxItems) {
            throw invalid("最多选择" + maxItems + "项: " + input.getToolCallId());
        }
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static AiBusinessException invalid(String detail) {
        return new AiBusinessException(AiErrorCode.INVALID_PARAMETER, detail);
    }

    private static AiBusinessException contextMismatch(ChatRunEntity run, String detail) {
        log.warn("挂起输入上下文不一致: runId={}, phaseNo={}, detail={}", run.getId(), run.getPhaseNo(), detail);
        return new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
    }
}
