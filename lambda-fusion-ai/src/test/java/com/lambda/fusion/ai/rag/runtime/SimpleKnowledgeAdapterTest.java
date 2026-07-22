package com.lambda.fusion.ai.rag.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeBaseEntity;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link SimpleKnowledgeAdapter} 抽出的纯逻辑：跨知识库合并按分数降序截断、
 * 知识库级 limit/scoreThreshold 覆盖全局默认值、向量库后端按类型分流
 * （MEMORY 零配置可用 / 未知类型拒绝）。不连真实 pgvector。
 *
 * @author Jin
 */
class SimpleKnowledgeAdapterTest {

    @Test
    void mergeSortsByScoreDescending() {
        List<RetrievedChunk> merged = new ArrayList<>(List.of(
                new RetrievedChunk("low", 0.3, "kb1", "doc1"),
                new RetrievedChunk("high", 0.9, "kb2", "doc2"),
                new RetrievedChunk("mid", 0.6, "kb1", "doc1")));

        List<RetrievedChunk> result = SimpleKnowledgeAdapter.mergeAndTruncate(merged, 5);

        assertThat(result).extracting(RetrievedChunk::content).containsExactly("high", "mid", "low");
    }

    @Test
    void mergeTruncatesToLimit() {
        List<RetrievedChunk> merged = new ArrayList<>(List.of(
                new RetrievedChunk("a", 0.9, "kb1", "doc1"),
                new RetrievedChunk("b", 0.8, "kb1", "doc1"),
                new RetrievedChunk("c", 0.7, "kb2", "doc2")));

        List<RetrievedChunk> result = SimpleKnowledgeAdapter.mergeAndTruncate(merged, 2);

        assertThat(result).extracting(RetrievedChunk::content).containsExactly("a", "b");
    }

    @Test
    void mergeKeepsAllWhenBelowLimit() {
        List<RetrievedChunk> merged = new ArrayList<>(List.of(new RetrievedChunk("a", 0.9, "kb1", "doc1")));

        assertThat(SimpleKnowledgeAdapter.mergeAndTruncate(merged, 5)).hasSize(1);
    }

    @Test
    void kbLevelLimitOverridesDefault() {
        AiProperties.Rag rag = new AiProperties.Rag(); // defaultLimit = 5
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();

        assertThat(SimpleKnowledgeAdapter.resolveLimit(kb, rag)).isEqualTo(5);

        kb.setRetrieveLimit(3);
        assertThat(SimpleKnowledgeAdapter.resolveLimit(kb, rag)).isEqualTo(3);
    }

    @Test
    void kbLevelScoreThresholdOverridesDefault() {
        AiProperties.Rag rag = new AiProperties.Rag(); // defaultScoreThreshold = 0.5
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();

        assertThat(SimpleKnowledgeAdapter.resolveScoreThreshold(kb, rag)).isEqualTo(0.5);

        kb.setScoreThreshold(new BigDecimal("0.750"));
        assertThat(SimpleKnowledgeAdapter.resolveScoreThreshold(kb, rag)).isEqualTo(0.75);
    }

    @Test
    void memoryStoreCreatedWithoutPgvectorConfig() {
        AiProperties.Rag rag = new AiProperties.Rag(); // store.type=MEMORY 默认，pgvector 未配置

        VDBStoreBase store = SimpleKnowledgeAdapter.createStore(rag, null, 1536);

        assertThat(store).isInstanceOf(InMemoryStore.class);
        assertThat(((InMemoryStore) store).getDimensions()).isEqualTo(1536);
    }

    @Test
    void pgvectorStoreRequiresJdbcUrl() {
        AiProperties.Rag rag = new AiProperties.Rag();
        rag.getStore().setType("PGVECTOR"); // jdbcUrl 未配置
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setVectorTable("ai_kb_x");

        assertThatThrownBy(() -> SimpleKnowledgeAdapter.createStore(rag, kb, 1536))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void unknownStoreTypeRejected() {
        AiProperties.Rag rag = new AiProperties.Rag();
        rag.getStore().setType("QDRANT");

        assertThatThrownBy(() -> SimpleKnowledgeAdapter.createStore(rag, null, 1536))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void overrideLimitTakesPrecedenceOverKbLevel() {
        AiProperties.Rag rag = new AiProperties.Rag(); // defaultLimit = 5
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setRetrieveLimit(3);

        // 调用方显式 limit（Agentic 工具）优先于知识库级与全局默认
        assertThat(SimpleKnowledgeAdapter.resolveLimit(kb, rag, 10)).isEqualTo(10);
        // null 时回落知识库级
        assertThat(SimpleKnowledgeAdapter.resolveLimit(kb, rag, null)).isEqualTo(3);
        // 合并截断条数：limit 优先，null 回落全局默认
        assertThat(SimpleKnowledgeAdapter.resolveFinalLimit(rag, 10)).isEqualTo(10);
        assertThat(SimpleKnowledgeAdapter.resolveFinalLimit(rag, null)).isEqualTo(5);
    }
}
