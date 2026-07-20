package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.middleware.MiddlewareBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按 {@code ai_app.middleware_config} 构造 {@link MiddlewareBase} 列表。本期支持：
 * <ul>
 *   <li>{@code rate_limit} -> {@link RateLimitMiddleware}（{@code params.maxConcurrent}，默认 10）</li>
 * </ul>
 * 记账已由事件流 usage 聚合（{@code EventToSseAdapter} -> {@code CostCalculator}）处理，OpenTelemetry
 * trace 为后续里程碑，均不归此处。未知 type 跳过并告警。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiddlewareFactory {

    private final MiddlewareConfigParser middlewareConfigParser;

    public List<MiddlewareBase> build(String middlewareConfig) {
        List<MiddlewareConfigDto> configs = middlewareConfigParser.parse(middlewareConfig);
        List<MiddlewareBase> middlewares = new ArrayList<>();
        for (MiddlewareConfigDto config : configs) {
            if (Boolean.FALSE.equals(config.enabled())) {
                continue;
            }
            MiddlewareBase mw = create(config);
            if (mw != null) {
                middlewares.add(mw);
            }
        }
        return middlewares;
    }

    private MiddlewareBase create(MiddlewareConfigDto config) {
        if (config.type() == null) {
            return null;
        }
        if ("rate_limit".equalsIgnoreCase(config.type())) {
            int max = readInt(config.params(), "maxConcurrent", 10);
            return new RateLimitMiddleware(max);
        }
        log.warn("MiddlewareFactory: 未知 middleware type: {}，跳过", config.type());
        return null;
    }

    private static int readInt(Map<String, Object> params, String key, int def) {
        if (params == null) {
            return def;
        }
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return def;
    }
}
