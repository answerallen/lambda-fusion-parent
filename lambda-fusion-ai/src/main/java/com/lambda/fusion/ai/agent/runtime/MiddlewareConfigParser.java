package com.lambda.fusion.ai.agent.runtime;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 解析 {@code ai_app.middleware_config}（JSON 数组）为 {@link MiddlewareConfigDto} 列表。解析失败
 * 不阻断 agent 构造，返回空列表（agent 无横切中间件）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiddlewareConfigParser {

    private final ObjectMapper objectMapper;

    public List<MiddlewareConfigDto> parse(String middlewareConfig) {
        if (!StringUtils.hasText(middlewareConfig)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(middlewareConfig, new TypeReference<List<MiddlewareConfigDto>>() {});
        } catch (Exception e) {
            log.warn("MiddlewareConfigParser: middlewareConfig 解析失败，跳过中间件装配: {}", e.getMessage());
            return List.of();
        }
    }
}
