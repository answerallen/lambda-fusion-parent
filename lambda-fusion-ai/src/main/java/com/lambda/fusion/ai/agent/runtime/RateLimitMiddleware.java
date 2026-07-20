package com.lambda.fusion.ai.agent.runtime;

import com.lambda.fusion.ai.exception.AiBusinessException;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 并发限流中间件（{@code middleware_config} type={@code rate_limit}）：{@link #onAgent} 拦截整个 agent
 * 调用，{@link Semaphore} 限制同一 app 模板下同时执行的 agent 数。超 {@code maxConcurrent} 立即拒绝
 * （{@link AiBusinessException#tooManyRequests()}）。非 Spring bean，由 {@link MiddlewareFactory} 按
 * app 配置构造。
 *
 * @author Jin
 */
@Slf4j
public class RateLimitMiddleware implements MiddlewareBase {

    private final Semaphore semaphore;

    public RateLimitMiddleware(int maxConcurrent) {
        this.semaphore = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        if (!semaphore.tryAcquire()) {
            log.warn("RateLimitMiddleware: 并发超限，拒绝 sessionId={}", ctx.getSessionId());
            return Flux.error(AiBusinessException.tooManyRequests());
        }
        return next.apply(input).doFinally(sig -> semaphore.release());
    }
}
