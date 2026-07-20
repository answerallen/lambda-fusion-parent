package com.lambda.fusion.ai.agent.runtime;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 解析 {@code ai_app.subagent_spec}（JSON 数组）为 {@link SubagentSpecDto} 列表。解析失败不阻断
 * agent 构造，返回空列表（主 agent 无子 agent 能力）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubagentSpecParser {

    private final ObjectMapper objectMapper;

    public List<SubagentSpecDto> parse(String subagentSpec) {
        if (!StringUtils.hasText(subagentSpec)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(subagentSpec, new TypeReference<List<SubagentSpecDto>>() {});
        } catch (Exception e) {
            log.warn("SubagentSpecParser: subagentSpec 解析失败，跳过子 agent 装配: {}", e.getMessage());
            return List.of();
        }
    }
}
