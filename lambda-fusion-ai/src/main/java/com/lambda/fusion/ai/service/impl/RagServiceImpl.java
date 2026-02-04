package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.model.RagResult;
import com.lambda.fusion.ai.model.VectorSearchResult;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.repository.VectorRepository;
import com.lambda.fusion.ai.service.RagService;
import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
    private final PromptTemplateMapper promptTemplateMapper;
    private final EmbeddingModel embeddingModel;

    private final ChatModelFactory chatModelFactory;

    @Override
    public List<VectorSearchResult> retrieve(String query, Long kbId, Integer topK, Double minScore) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        Response<Embedding> embeddingResponse = embeddingModel.embed(query);
        List<Double> queryVector = embeddingResponse.content().vectorAsList().stream()
                .map(Float::doubleValue)
                .collect(Collectors.toList());

        String tableName = kb.getVectorTableName();
        Integer limit = topK != null ? topK : (kb.getRetrievalTopK() != null ? kb.getRetrievalTopK() : 5);
        Double scoreThreshold = minScore != null
                ? minScore
                : (kb.getSimilarityThreshold() != null
                        ? kb.getSimilarityThreshold().doubleValue()
                        : 0.6);

        // 双路搜寻: 向量搜索 + 关键词搜索
        List<VectorSearchResult> vectorResults =
                vectorRepository.searchSimilar(tableName, queryVector, limit * 2, scoreThreshold);
        List<VectorSearchResult> keywordResults = vectorRepository.searchKeyword(tableName, query, limit * 2);

        // 使用RRF算法进行结果融合
        return reciprocalRankFusion(vectorResults, keywordResults, limit);
    }

    private List<VectorSearchResult> reciprocalRankFusion(
            List<VectorSearchResult> vectorResults, List<VectorSearchResult> keywordResults, int topK) {

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, VectorSearchResult> idToResult = new HashMap<>();
        int k = 60;

        for (int i = 0; i < vectorResults.size(); i++) {
            VectorSearchResult res = vectorResults.get(i);
            String vectorId = res.getVectorId();
            rrfScores.put(vectorId, rrfScores.getOrDefault(vectorId, 0.0) + 1.0 / (k + i + 1));
            idToResult.putIfAbsent(vectorId, res);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            VectorSearchResult res = keywordResults.get(i);
            String vectorId = res.getVectorId();
            rrfScores.put(vectorId, rrfScores.getOrDefault(vectorId, 0.0) + 1.0 / (k + i + 1));
            idToResult.putIfAbsent(vectorId, res);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorSearchResult res = idToResult.get(entry.getKey());
                    res.setScore(entry.getValue());
                    return res;
                })
                .collect(Collectors.toList());
    }

    @Override
    public RagResult chat(String query, Long kbId, Long llmModelId) {
        List<VectorSearchResult> searchResults = retrieve(query, kbId, null, null);
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);

        String context =
                searchResults.stream().map(VectorSearchResult::getContent).collect(Collectors.joining("\n\n"));
        String templateContent = loadPromptTemplate(kb.getCategory());

        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", query);

        Prompt prompt = PromptTemplate.from(templateContent).apply(variables);

        // 使用Factory获取模型(OpenAI/Ollama等)
        ChatModel chatModel = chatModelFactory.getChatModel(llmModelId);
        UserMessage userMessage = prompt.toUserMessage();

        ChatResponse response = chatModel.chat(userMessage);
        String answer = response.aiMessage().text();

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
    public List<VectorSearchResult> streamChat(
            String query,
            Long kbId,
            List<VectorSearchResult> retrievedChunks,
            Long llmModelId,
            StreamingChatResponseHandler handler) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        List<VectorSearchResult> searchResults = retrievedChunks;
        if (searchResults == null) {
            searchResults = retrieve(query, kbId, null, null);
        }

        String context =
                searchResults.stream().map(VectorSearchResult::getContent).collect(Collectors.joining("\n\n"));
        String templateContent = loadPromptTemplate(kb.getCategory());

        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", query);

        Prompt prompt = PromptTemplate.from(templateContent).apply(variables);

        // 使用Factory获取流式模型
        StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(llmModelId);

        streamingModel.chat(prompt.text(), handler);

        return searchResults;
    }

    private String loadPromptTemplate(String category) {
        // 1. 尝试按分类加载系统内置模板
        PromptTemplateEntity template = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getCategory, category)
                .eq(PromptTemplateEntity::getIsSystem, true)
                .eq(PromptTemplateEntity::getEnabled, true)
                .last("LIMIT 1"));

        if (template != null) {
            return template.getTemplateContent();
        }

        // 2. 备选方案：加载默认RAG模板
        template = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTemplateId, "system_rag_default")
                .eq(PromptTemplateEntity::getEnabled, true)
                .last("LIMIT 1"));

        if (template != null) {
            return template.getTemplateContent();
        }

        // 3. 最后保底：硬编码模板（不推荐，但为容错提供）
        return "基于以下背景回答问题:\n{{context}}\n\n问题: {{question}}";
    }
}
