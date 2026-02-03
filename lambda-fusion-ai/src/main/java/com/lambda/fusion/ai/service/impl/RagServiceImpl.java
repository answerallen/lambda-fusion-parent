package com.lambda.fusion.ai.service.impl;

import com.lambda.fusion.ai.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.dto.RagResult;
import com.lambda.fusion.ai.model.dto.VectorSearchResultDTO;
import com.lambda.fusion.ai.repository.VectorRepository;
import com.lambda.fusion.ai.service.RagService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RAG服务实现类
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorRepository vectorRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingModel embeddingModel;

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;

    @Override
    public List<VectorSearchResultDTO> retrieve(String query, Long kbId, Integer topK, Double minScore) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        Response<Embedding> embeddingResponse = embeddingModel.embed(query);
        List<Double> queryVector = embeddingResponse.content().vectorAsList().stream()
                .map(Float::doubleValue)
                .collect(Collectors.toList());

        String tableName = kb.getVectorTableName();
        Integer limit = topK != null ? topK : kb.getRetrievalTopK();
        if (limit == null) limit = 5;

        Double scoreThreshold = minScore != null ? minScore : 0.6;

        // 双路搜寻: 向量搜索 + 关键词搜索
        List<VectorSearchResultDTO> vectorResults =
                vectorRepository.searchSimilar(tableName, queryVector, limit * 2, scoreThreshold);
        List<VectorSearchResultDTO> keywordResults = vectorRepository.searchKeyword(tableName, query, limit * 2);

        // 使用 RRF 算法进行结果融合
        return reciprocalRankFusion(vectorResults, keywordResults, limit);
    }

    /**
     * Reciprocal Rank Fusion (RRF) 算法实现
     */
    private List<VectorSearchResultDTO> reciprocalRankFusion(
            List<VectorSearchResultDTO> vectorResults, List<VectorSearchResultDTO> keywordResults, int topK) {

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, VectorSearchResultDTO> idToResult = new HashMap<>();
        int k = 60;

        for (int i = 0; i < vectorResults.size(); i++) {
            VectorSearchResultDTO res = vectorResults.get(i);
            String vectorId = res.getVectorId();
            rrfScores.put(vectorId, rrfScores.getOrDefault(vectorId, 0.0) + 1.0 / (k + i + 1));
            idToResult.putIfAbsent(vectorId, res);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            VectorSearchResultDTO res = keywordResults.get(i);
            String vectorId = res.getVectorId();
            rrfScores.put(vectorId, rrfScores.getOrDefault(vectorId, 0.0) + 1.0 / (k + i + 1));
            idToResult.putIfAbsent(vectorId, res);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorSearchResultDTO res = idToResult.get(entry.getKey());
                    res.setScore(entry.getValue());
                    return res;
                })
                .collect(Collectors.toList());
    }

    @Override
    public RagResult chat(String query, Long kbId) {
        List<VectorSearchResultDTO> searchResults = retrieve(query, kbId, 5, 0.6);
        String context =
                searchResults.stream().map(VectorSearchResultDTO::getContent).collect(Collectors.joining("\n\n"));

        String template =
                "基于以下已知信息，回答用户的问题。如果无法从中得到答案，请说'不知道'，不要编造信息。\n\n" + "已知信息：\n{{context}}\n\n" + "问题：{{question}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", query);

        Prompt prompt = PromptTemplate.from(template).apply(variables);

        Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());
        String answer = response.content().text();

        return RagResult.builder()
                .answer(answer)
                .retrievedChunks(searchResults)
                .prompt(prompt.text())
                .promptTokens(
                        response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0)
                .completionTokens(
                        response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0)
                .build();
    }

    @Override
    public List<VectorSearchResultDTO> streamChat(
            String query, Long kbId, StreamingResponseHandler<AiMessage> handler) {
        List<VectorSearchResultDTO> searchResults = retrieve(query, kbId, 5, 0.6);
        String context =
                searchResults.stream().map(VectorSearchResultDTO::getContent).collect(Collectors.joining("\n\n"));

        String template =
                "基于以下已知信息，回答用户的问题。如果无法从中得到答案，请说'不知道'，不要编造信息。\n\n" + "已知信息：\n{{context}}\n\n" + "问题：{{question}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", query);

        Prompt prompt = PromptTemplate.from(template).apply(variables);

        streamingChatLanguageModel.generate(prompt.toUserMessage(), handler);

        return searchResults;
    }
}
