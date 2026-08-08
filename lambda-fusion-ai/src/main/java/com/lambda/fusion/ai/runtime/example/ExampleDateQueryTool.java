package com.lambda.fusion.ai.runtime.example;

import com.lambda.fusion.ai.runtime.annotaion.RequireConfirm;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日期查询示例工具：演示 agentscope {@code @Tool} 工具定义 + ASK 权限（HITL）触发。
 *
 * <p>本工具是 HITL（人工确认）演示示例：通过 {@link RequireConfirm} 注解声明需要确认，
 * {@code ToolkitAssembler} 启动时扫描收集，{@code AgentFactory} 据此构建
 * {@code PermissionContextState.askRules}。模型调用本工具时 agentscope 的 {@code PermissionEngine}
 * 命中 ask 规则，发出 {@code RequireUserConfirmEvent}，agent 暂停等待用户确认后继续执行--
 * 用于验证 AG-UI HITL 链路（前端确认 UI -> 回传端点 -> 第二次 streamEvents 携带
 * {@code Msg.METADATA_CONFIRM_RESULTS} 恢复）。
 *
 * <p><b>注意</b>：日期查询本身是只读操作，此处配 ASK 仅为演示 HITL 机制。真实场景应对有副作用
 * 或敏感操作的工具（发消息、删除数据、执行命令等）配置 ASK；只读工具一般不需要确认。
 *
 * <p>作为 {@code @Component} 全局 Bean，由 {@code ToolkitAssembler} 扫描注册到所有应用的
 * {@code Toolkit}（与按 app 绑定的 {@code KnowledgeRetrievalTool} 不同）。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ExampleDateQueryTool {

    /** 工具名，{@code PermissionContextState.askRules} 以此为 toolName 锚点。 */
    public static final String TOOL_NAME = "query_date";

    @Tool(
            name = TOOL_NAME,
            description = "Query the current date/time with a custom format and timezone. "
                    + "Use this tool when the user asks about the current date, time, or weekday.")
    @RequireConfirm("日期查询演示 HITL 人工确认")
    public String queryDate(
            @ToolParam(
                            name = "format",
                            description = "Date/time format pattern, e.g. yyyy-MM-dd or yyyy-MM-dd HH:mm:ss")
                    String format,
            @ToolParam(
                            name = "timezone",
                            description = "IANA timezone ID, e.g. Asia/Shanghai or UTC. Defaults to system timezone.",
                            required = false)
                    String timezone) {
        String pattern = (format == null || format.isBlank()) ? "yyyy-MM-dd" : format;
        DateTimeFormatter formatter = resolveFormatter(pattern);
        ZoneId zone = resolveZone(timezone);
        String result = ZonedDateTime.now(zone).format(formatter);
        log.info("日期查询: pattern={}, timezone={}, result={}", pattern, zone.getId(), result);
        return result;
    }

    private static DateTimeFormatter resolveFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            log.warn("日期格式非法，回退默认 yyyy-MM-dd: pattern={}", pattern);
            return DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }
    }

    private static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("时区非法，回退系统默认: timezone={}", timezone);
            return ZoneId.systemDefault();
        }
    }
}
