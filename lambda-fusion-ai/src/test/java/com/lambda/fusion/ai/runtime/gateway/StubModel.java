package com.lambda.fusion.ai.runtime.gateway;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

/** 测试用桩 {@link Model}：返回固定文本，记录最大并发度以验证串行锁。 */
class StubModel implements Model {

    static final Duration DELAY = Duration.ofMillis(80);

    private final String response;
    private final AtomicInteger active = new AtomicInteger(0);
    private final AtomicInteger maxActive = new AtomicInteger(0);

    StubModel(String response) {
        this.response = response;
    }

    int maxActive() {
        return maxActive.get();
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
                    int cur = active.incrementAndGet();
                    maxActive.accumulateAndGet(cur, Math::max);
                    return Flux.just(createTextResponse(response));
                })
                .delayElements(DELAY)
                .doFinally(s -> active.decrementAndGet());
    }

    @Override
    public String getModelName() {
        return "stub-model";
    }

    private static ChatResponse createTextResponse(String text) {
        return ChatResponse.builder()
                .id("msg_" + UUID.randomUUID())
                .content(List.of(TextBlock.builder().text(text).build()))
                .usage(new ChatUsage(1, 1, 2))
                .build();
    }
}
