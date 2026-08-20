package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class MemoryModelGuardTest {

    @Test
    void rejectsLengthFinishReason() {
        MemoryModelGuard guard = new MemoryModelGuard(
                model(options -> Flux.just(response("partial", "length"))), 4096, Duration.ofMinutes(1));

        StepVerifier.create(guard.stream(List.of(), null, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("incomplete finish reason: length"))
                .verify();
    }

    @Test
    void acceptsStopAndMissingFinishReason() {
        MemoryModelGuard guard = new MemoryModelGuard(
                model(options -> Flux.just(response("first", null), response("second", "stop"))),
                4096,
                Duration.ofMinutes(1));

        StepVerifier.create(guard.stream(List.of(), null, null))
                .assertNext(response -> assertThat(text(response)).isEqualTo("first"))
                .assertNext(response -> assertThat(text(response)).isEqualTo("second"))
                .verifyComplete();
    }

    @Test
    void capsPortableMaxTokensAndPreservesOtherOptions() {
        AtomicReference<GenerateOptions> actualOptions = new AtomicReference<>();
        MemoryModelGuard guard = new MemoryModelGuard(
                model(options -> {
                    actualOptions.set(options);
                    return Flux.just(response("complete", "stop"));
                }),
                4096,
                Duration.ofMinutes(1));
        GenerateOptions requested =
                GenerateOptions.builder().temperature(0.2).maxTokens(8192).build();

        guard.stream(List.of(), null, requested).collectList().block();

        assertThat(actualOptions.get().getMaxTokens()).isEqualTo(4096);
        assertThat(actualOptions.get().getMaxCompletionTokens()).isNull();
        assertThat(actualOptions.get().getTemperature()).isEqualTo(0.2);
    }

    @Test
    void keepsSmallerMaxCompletionTokensWithoutAddingMaxTokens() {
        AtomicReference<GenerateOptions> actualOptions = new AtomicReference<>();
        MemoryModelGuard guard = new MemoryModelGuard(
                model(options -> {
                    actualOptions.set(options);
                    return Flux.just(response("complete", "stop"));
                }),
                4096,
                Duration.ofMinutes(1));
        GenerateOptions requested =
                GenerateOptions.builder().maxCompletionTokens(1024).build();

        guard.stream(List.of(), null, requested).collectList().block();

        assertThat(actualOptions.get().getMaxTokens()).isNull();
        assertThat(actualOptions.get().getMaxCompletionTokens()).isEqualTo(1024);
    }

    @Test
    void failsWhenWholeCallExceedsTimeout() {
        Duration timeout = Duration.ofSeconds(5);
        MemoryModelGuard guard = new MemoryModelGuard(model(options -> Flux.never()), 4096, timeout);

        StepVerifier.withVirtualTime(() -> guard.stream(List.of(), null, null))
                .expectSubscription()
                .thenAwait(timeout)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("did not complete within PT5S"))
                .verify();
    }

    private static Model model(Function<GenerateOptions, Flux<ChatResponse>> stream) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return stream.apply(options);
            }

            @Override
            public String getModelName() {
                return "memory-test-model";
            }
        };
    }

    private static ChatResponse response(String text, String finishReason) {
        return ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(text).build()))
                .finishReason(finishReason)
                .build();
    }

    private static String text(ChatResponse response) {
        return ((TextBlock) response.getContent().getFirst()).getText();
    }
}
