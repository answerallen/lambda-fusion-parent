package com.lambda.fusion.ai.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 为 AgentScope 长期记忆调用提供完整性边界。
 *
 * <p>记忆 flush 与 consolidation 都会在模型流正常完成后写文件，因此本适配器把明确截断、内容过滤和总时长超限
 * 转换为流错误，使 AgentScope 跳过文件写入及 consolidation watermark 推进。记忆调用不需要增量消费，故先完整收集并
 * 校验响应，再向下游重放，保证调用级原子性。
 */
final class MemoryModelGuard implements Model {

    private final Model delegate;
    private final int maxOutputTokens;
    private final Duration timeout;

    MemoryModelGuard(Model delegate, int maxOutputTokens, Duration timeout) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        GenerateOptions boundedOptions = boundOutputTokens(options);
        return Flux.defer(() -> delegate.stream(messages, tools, boundedOptions))
                .<ChatResponse>handle((response, sink) -> {
                    if (isIncomplete(response.getFinishReason())) {
                        sink.error(new IllegalStateException(
                                "Memory model returned incomplete finish reason: " + response.getFinishReason()));
                        return;
                    }
                    sink.next(response);
                })
                .collectList()
                .timeout(
                        timeout,
                        Mono.error(new IllegalStateException("Memory model did not complete within " + timeout)))
                .flatMapMany(Flux::fromIterable);
    }

    private GenerateOptions boundOutputTokens(GenerateOptions options) {
        Integer requestedMaxTokens = options == null ? null : options.getMaxTokens();
        Integer requestedMaxCompletionTokens = options == null ? null : options.getMaxCompletionTokens();
        GenerateOptions.Builder guard = GenerateOptions.builder();
        if (requestedMaxCompletionTokens != null) {
            guard.maxCompletionTokens(Math.min(requestedMaxCompletionTokens, maxOutputTokens));
        }
        if (requestedMaxTokens != null || requestedMaxCompletionTokens == null) {
            guard.maxTokens(
                    requestedMaxTokens == null ? maxOutputTokens : Math.min(requestedMaxTokens, maxOutputTokens));
        }
        return GenerateOptions.mergeOptions(guard.build(), options);
    }

    private static boolean isIncomplete(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return false;
        }
        String normalized =
                finishReason.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.contains("length")
                || normalized.contains("max_token")
                || normalized.equals("content_filter")
                || normalized.equals("safety")
                || normalized.equals("recitation")
                || normalized.equals("blocklist")
                || normalized.equals("prohibited_content")
                || normalized.equals("spii")
                || normalized.equals("error")
                || normalized.equals("cancelled")
                || normalized.equals("canceled");
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public boolean supportsNativeStructuredOutput() {
        return delegate.supportsNativeStructuredOutput();
    }

    @Override
    public boolean supportsNativeStructuredOutputWithTools() {
        return delegate.supportsNativeStructuredOutputWithTools();
    }

    @Override
    public int getContextWindowSize() {
        return delegate.getContextWindowSize();
    }
}
