package com.lambda.fusion.ai.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.support.embedding.EmbeddingModelManager;
import com.lambda.fusion.ai.commons.support.vector.VectorDimensionProcessor;
import com.lambda.fusion.ai.commons.utils.AgentNodeUtils;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.mapper.VectorRepository;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceRegressionTest {

    @Test
    @DisplayName("RAG检索-向量化返回content为空时抛出业务异常")
    void ragRetrieveShouldFailWhenEmbeddingContentIsNull() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId("kb-1");
        kb.setEmbeddingModel("model-1");
        kb.setEmbeddingDimension(1536);

        KnowledgeBaseMapper knowledgeBaseMapper = proxy(
                KnowledgeBaseMapper.class,
                (method, args) -> "selectById".equals(method.getName()) ? kb : defaultValue(method.getReturnType()));
        VectorRepository vectorRepository =
                proxy(VectorRepository.class, (method, args) -> defaultValue(method.getReturnType()));
        PromptTemplateMapper promptTemplateMapper =
                proxy(PromptTemplateMapper.class, (method, args) -> defaultValue(method.getReturnType()));

        EmbeddingModel embeddingModel = proxy(EmbeddingModel.class, (method, args) -> {
            if ("embed".equals(method.getName())) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        EmbeddingModelManager embeddingModelManager = new StubEmbeddingModelManager(embeddingModel);

        RagServiceImpl service = new RagServiceImpl(
                vectorRepository,
                knowledgeBaseMapper,
                promptTemplateMapper,
                embeddingModelManager,
                new VectorDimensionProcessor(),
                null,
                null);

        assertThatThrownBy(() -> service.retrieve("hello", "kb-1", null, null))
                .isInstanceOf(AiBusinessException.class)
                .hasMessageContaining("向量化失败");
    }

    @Test
    @DisplayName("工作流执行服务-Token取值支持字符串数字")
    void agentNodeUtilsAsIntShouldSupportStringNumber() {
        int value = AgentNodeUtils.asInt("123");
        int invalid = AgentNodeUtils.asInt("not-a-number");

        assertThat(value).isEqualTo(123);
        assertThat(invalid).isZero();
    }

    @Test
    @DisplayName("会话统计异步更新-失败时吞掉异常并完成Future")
    void asyncSessionStatisticsShouldCompleteFutureOnError() {
        AtomicSessionUpdateServiceImpl service = new AtomicSessionUpdateServiceImpl(null, null) {
            @Override
            public void updateSessionStatisticsOptimistic(String sessionId, int messageIncrement, int tokenIncrement) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatCode(() -> service.updateSessionStatisticsAsync("s-1", 1, 2).join())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("会话统计异步更新-成功时返回已完成Future")
    void asyncSessionStatisticsShouldReturnCompletedFutureOnSuccess() {
        AtomicSessionUpdateServiceImpl service = new AtomicSessionUpdateServiceImpl(null, null) {
            @Override
            public void updateSessionStatisticsOptimistic(String sessionId, int messageIncrement, int tokenIncrement) {
                // no-op
            }
        };

        assertThatCode(() -> service.updateSessionStatisticsAsync("s-1", 1, 2).join())
                .doesNotThrowAnyException();
    }

    private interface InvocationHandlerWithMethod {
        Object invoke(Method method, Object[] args) throws InvocationTargetException;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandlerWithMethod handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> handler.invoke(method, args);
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        return null;
    }

    private static final class StubEmbeddingModelManager extends EmbeddingModelManager {
        private final EmbeddingModel embeddingModel;

        private StubEmbeddingModelManager(EmbeddingModel embeddingModel) {
            super(null, null);
            this.embeddingModel = embeddingModel;
        }

        @Override
        public EmbeddingModel getModelByKnowledgeBase(String embeddingModel) {
            return this.embeddingModel;
        }
    }
}
