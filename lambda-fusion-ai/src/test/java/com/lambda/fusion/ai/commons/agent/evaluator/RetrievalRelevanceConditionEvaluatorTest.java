package com.lambda.fusion.ai.commons.agent.evaluator;

import static com.lambda.cloud.test.assertion.LambdaAssertions.*;

import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import org.junit.jupiter.api.*;

class RetrievalRelevanceConditionEvaluatorTest {

    private RetrievalRelevanceConditionEvaluator evaluator;
    private AgentState state;

    @BeforeEach
    void setUp() {
        evaluator = new RetrievalRelevanceConditionEvaluator();
        state = new AgentState();
    }

    @Test
    @DisplayName("测试空表达式返回true")
    void testEmptyExpression() {
        assertThat(evaluator.evaluate(null, state)).isTrue();
        assertThat(evaluator.evaluate("", state)).isTrue();
        assertThat(evaluator.evaluate("   ", state)).isTrue();
    }

    @Test
    @DisplayName("测试hasRelevantDocs表达式")
    void testHasRelevantDocsExpression() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.8));
        results.add(Map.of("relevance", 0.7));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("hasRelevantDocs", state)).isTrue();
    }

    @Test
    @DisplayName("测试hasRelevantDocs-低相关性")
    void testHasRelevantDocsLowRelevance() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.3));
        results.add(Map.of("relevance", 0.2));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("hasRelevantDocs", state)).isFalse();
    }

    @Test
    @DisplayName("测试docCount表达式")
    void testDocCountExpression() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.8));
        results.add(Map.of("relevance", 0.7));
        results.add(Map.of("relevance", 0.6));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("docCount >= 3", state)).isTrue();
        assertThat(evaluator.evaluate("docCount > 2", state)).isTrue();
        assertThat(evaluator.evaluate("docCount == 3", state)).isTrue();
        assertThat(evaluator.evaluate("docCount < 5", state)).isTrue();
    }

    @Test
    @DisplayName("测试relevance表达式")
    void testRelevanceExpression() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.8));
        results.add(Map.of("relevance", 0.7));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("relevance > 0.5", state)).isTrue();
        assertThat(evaluator.evaluate("relevance >= 0.75", state)).isTrue();
    }

    @Test
    @DisplayName("测试maxRelevance表达式")
    void testMaxRelevanceExpression() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.9));
        results.add(Map.of("relevance", 0.7));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("maxRelevance >= 0.9", state)).isTrue();
        assertThat(evaluator.evaluate("maxRelevance > 0.8", state)).isTrue();
    }

    @Test
    @DisplayName("测试minRelevance表达式")
    void testMinRelevanceExpression() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("relevance", 0.9));
        results.add(Map.of("relevance", 0.6));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("minRelevance >= 0.5", state)).isTrue();
        assertThat(evaluator.evaluate("minRelevance < 0.7", state)).isTrue();
    }

    @Test
    @DisplayName("测试score字段替代relevance")
    void testScoreFieldAlternative() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("score", 0.85));
        results.add(Map.of("score", 0.75));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("relevance > 0.7", state)).isTrue();
    }

    @Test
    @DisplayName("测试平均相关性仅按有效分数字段计算")
    void testAverageRelevanceUsesOnlyScoredDocuments() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("score", 0.9));
        results.add(Map.of("title", "missing-score"));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("relevance > 0.8", state)).isTrue();
        assertThat(evaluator.evaluate("docCount == 2", state)).isTrue();
    }

    @Test
    @DisplayName("测试similarity字段")
    void testSimilarityField() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("similarity", 0.9));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("relevance > 0.8", state)).isTrue();
    }

    @Test
    @DisplayName("测试distance字段转换")
    void testDistanceFieldConversion() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("distance", 0.1));
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("relevance > 0.8", state)).isTrue();
    }

    @Test
    @DisplayName("测试空结果列表")
    void testEmptyResultsList() {
        List<Map<String, Object>> results = new ArrayList<>();
        state.getAttributes().put("retrievalResults", results);

        assertThat(evaluator.evaluate("hasRelevantDocs", state)).isFalse();
        assertThat(evaluator.evaluate("docCount == 0", state)).isTrue();
    }

    @Test
    @DisplayName("测试无检索结果")
    void testNoRetrievalResults() {
        assertThat(evaluator.evaluate("hasRelevantDocs", state)).isFalse();
        assertThat(evaluator.evaluate("docCount == 0", state)).isTrue();
    }

    @Test
    @DisplayName("测试getType返回值")
    void testGetType() {
        assertThat(evaluator.getType()).isEqualTo("retrieval");
    }

    @Test
    @DisplayName("测试不支持的表达式格式")
    void testUnsupportedExpressionFormat() {
        state.getAttributes().put("retrievalResults", new ArrayList<>());
        assertThat(evaluator.evaluate("unknownExpression", state)).isFalse();
    }
}
