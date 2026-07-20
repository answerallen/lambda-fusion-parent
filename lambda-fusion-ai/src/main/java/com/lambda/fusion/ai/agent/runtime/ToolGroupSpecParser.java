package com.lambda.fusion.ai.agent.runtime;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 解析 {@code ai_app.tool_groups}（JSON 数组）为 {@link ToolGroupSpecDto} 列表。解析失败不阻断
 * agent 构造，返回空列表（agent 无工具组，全部工具默认激活）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGroupSpecParser {

    private final ObjectMapper objectMapper;

    public List<ToolGroupSpecDto> parse(String toolGroups) {
        if (!StringUtils.hasText(toolGroups)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(toolGroups, new TypeReference<List<ToolGroupSpecDto>>() {});
        } catch (Exception e) {
            log.warn("ToolGroupSpecParser: toolGroups 解析失败，跳过工具组装配: {}", e.getMessage());
            return List.of();
        }
    }
}
