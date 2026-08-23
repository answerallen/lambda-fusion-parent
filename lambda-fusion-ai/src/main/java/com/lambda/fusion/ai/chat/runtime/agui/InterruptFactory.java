package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.runtime.tools.UserInteractionTools;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * 输入型 HITL Interrupt 构造器：把挂起的交互工具调用转换为携带 {@code responseSchema} 的
 * AG-UI Interrupt 事件，供前端按交互类型渲染单选、多选或文本输入卡片。
 *
 * <p>{@code reason} 取官方 AG-UI 预留的 {@code input_required}，与 2.0.2+ 官方
 * 中断转换器上线后的契约保持一致；responseSchema 为标准 JSON Schema，属性名固定为
 * {@code choice}/{@code choices}/{@code text}，是恢复侧取值的契约。
 *
 * @author Jin
 */
public final class InterruptFactory {

    /** 输入型中断 reason：官方 AG-UI 预留值。 */
    public static final String REASON_INPUT_REQUIRED = "input_required";

    /** Interrupt metadata 键：工具名。 */
    public static final String METADATA_TOOL_NAME = "toolName";

    /** Interrupt metadata 键：交互类型。 */
    public static final String METADATA_INPUT_KIND = "inputKind";

    /** 交互类型：单选。 */
    public static final String KIND_SINGLE_CHOICE = "single_choice";

    /** 交互类型：多选。 */
    public static final String KIND_MULTI_CHOICE = "multi_choice";

    /** 交互类型：文本。 */
    public static final String KIND_TEXT = "text";

    private InterruptFactory() {}

    /**
     * 把一个挂起的交互工具调用构造为输入型 Interrupt。
     *
     * @param tool 挂起的工具调用块（来自 RequireExternalExecutionEvent）
     * @return 携带 responseSchema 与交互元数据的 Interrupt 事件
     */
    public static AguiEvent.Interrupt inputInterrupt(ToolUseBlock tool) {
        InputSpec spec = InputSpec.parse(tool);
        return new AguiEvent.Interrupt(
                tool.getId(),
                REASON_INPUT_REQUIRED,
                spec.question(),
                tool.getId(),
                spec.responseSchema(),
                null,
                Map.of(METADATA_TOOL_NAME, tool.getName(), METADATA_INPUT_KIND, spec.kind()));
    }

    /** 从工具入参解析的交互规格。 */
    private record InputSpec(
            String kind,
            String question,
            List<String> options,
            Integer minSelect,
            Integer maxSelect,
            Integer maxLength) {

        static InputSpec parse(ToolUseBlock tool) {
            Map<String, Object> input = tool.getInput() == null ? Map.of() : tool.getInput();
            return new InputSpec(
                    resolveKind(tool.getName()),
                    StringUtils.defaultIfBlank(text(input.get("question")), "请补充信息后继续"),
                    texts(input.get("options")),
                    integer(input.get("minSelect")),
                    integer(input.get("maxSelect")),
                    integer(input.get("maxLength")));
        }

        /** 按工具名解析交互类型；未声明的挂起工具按文本输入兜底。 */
        static String resolveKind(String toolName) {
            return switch (toolName == null ? "" : toolName) {
                case UserInteractionTools.SINGLE_CHOICE_TOOL -> KIND_SINGLE_CHOICE;
                case UserInteractionTools.MULTI_CHOICE_TOOL -> KIND_MULTI_CHOICE;
                default -> KIND_TEXT;
            };
        }

        /** 生成恢复值的 JSON Schema：单选 {@code choice}、多选 {@code choices}、文本 {@code text}。 */
        Map<String, Object> responseSchema() {
            Map<String, Object> property =
                    switch (kind) {
                        case KIND_SINGLE_CHOICE -> enumProperty();
                        case KIND_MULTI_CHOICE -> arrayProperty();
                        default -> textProperty();
                    };
            String name =
                    switch (kind) {
                        case KIND_SINGLE_CHOICE -> "choice";
                        case KIND_MULTI_CHOICE -> "choices";
                        default -> "text";
                    };
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of(name, property));
            schema.put("required", List.of(name));
            return schema;
        }

        /** 单选属性：string + 选项枚举。 */
        private Map<String, Object> enumProperty() {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            if (!options.isEmpty()) {
                property.put("enum", options);
            }
            return property;
        }

        /** 多选属性：string 数组 + 选项枚举与数量约束。 */
        private Map<String, Object> arrayProperty() {
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "string");
            if (!options.isEmpty()) {
                items.put("enum", options);
            }
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "array");
            property.put("items", items);
            if (minSelect != null) {
                property.put("minItems", minSelect);
            }
            if (maxSelect != null) {
                property.put("maxItems", maxSelect);
            }
            return property;
        }

        /** 文本属性：string + 长度约束。 */
        private Map<String, Object> textProperty() {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            if (maxLength != null) {
                property.put("maxLength", maxLength);
            }
            return property;
        }

        private static String text(Object value) {
            return value instanceof String s ? s : null;
        }

        private static List<String> texts(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }

        private static Integer integer(Object value) {
            return value instanceof Number number ? number.intValue() : null;
        }
    }
}
