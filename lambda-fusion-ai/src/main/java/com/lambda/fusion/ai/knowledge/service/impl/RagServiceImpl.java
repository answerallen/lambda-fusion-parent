package com.lambda.fusion.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.factory.AgentGraphProvider;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.knowledge.embedding.EmbeddingModelManager;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.mapper.VectorRepository;
import com.lambda.fusion.ai.knowledge.model.RagResult;
import com.lambda.fusion.ai.knowledge.model.VectorSearchResult;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.knowledge.service.RagService;
import com.lambda.fusion.ai.knowledge.vector.VectorDimensionProcessor;
import com.lambda.fusion.ai.prompt.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.prompt.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.utils.AgentUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final EmbeddingModelManager embeddingModelManager;
    private final VectorDimensionProcessor vectorDimensionProcessor;
    private final AgentGraphProvider agentGraphProvider;

    @Override
    public List<VectorSearchResult> retrieve(String query, String kbId, Integer topK, Double minScore) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbId);
        }

        // 获取知识库配置的 EmbeddingModel
        EmbeddingModel embeddingModel = embeddingModelManager.getModelByKnowledgeBase(kb.getEmbeddingModel());
        log.debug("使用 EmbeddingModel: {} 进行检索", kb.getEmbeddingModel());

        // 添加空指针检查
        Response<Embedding> embeddingResponse = embeddingModel.embed(query);
        if (embeddingResponse == null) {
            throw new AiBusinessException(AiErrorCode.EMBEDDING_FAILED, "向量化失败：返回结果为空");
        }

        List<Double> queryVector = embeddingResponse.content().vectorAsList().stream()
                .map(Float::doubleValue)
                .collect(Collectors.toList());

        // 添加维度检查
        Integer embeddingDimension = kb.getEmbeddingDimension();
        if (embeddingDimension == null || embeddingDimension <= 0) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "知识库向量维度配置无效: " + kbId);
        }

        int limit = Optional.ofNullable(topK)
                .orElseGet(() -> Optional.ofNullable(kb.getRetrievalTopK()).orElse(5));

        Double scoreThreshold = Optional.ofNullable(minScore)
                .or(() -> Optional.ofNullable(kb.getSimilarityThreshold()).map(Number::doubleValue))
                .orElse(0.6);

        // 获取存储维度，用于选择分表
        int storageDimension = vectorDimensionProcessor.getNearestSupportedDimension(embeddingDimension);
        if (queryVector.size() != storageDimension) {
            queryVector = vectorDimensionProcessor.normalizeToDimension(queryVector, storageDimension);
        }

        // 使用分表存储进行搜索
        List<VectorSearchResult> vectorResults = vectorRepository.searchSimilar(
                storageDimension, kbId, queryVector, limit * 2, scoreThreshold, kb.getTenantId());
        List<VectorSearchResult> keywordResults =
                vectorRepository.searchKeyword(storageDimension, kbId, query, limit * 2, kb.getTenantId());

        return reciprocalRankFusion(vectorResults, keywordResults, limit);
    }

    /**
     * 优化后的 RRF (Reciprocal Rank Fusion) 实现
     */
    private List<VectorSearchResult> reciprocalRankFusion(
            List<VectorSearchResult> vectorResults, List<VectorSearchResult> keywordResults, int topK) {

        // 1. 初始化容器
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, VectorSearchResult> idToResult = new HashMap<>();
        int k = 60; // RRF 算法常数

        // 2. 将所有搜索源放入列表，统一处理，消除重复代码
        List<List<VectorSearchResult>> allRankers = List.of(vectorResults, keywordResults);

        for (List<VectorSearchResult> results : allRankers) {
            for (int i = 0; i < results.size(); i++) {
                VectorSearchResult res = results.get(i);
                String id = res.getVectorId();

                // 计算当前排名的得分：1 / (k + rank)
                // rank 是 1-based，所以是 i + 1
                double score = 1.0 / (k + i + 1);

                // 使用 merge 代替 getOrDefault + put，代码更优雅
                rrfScores.merge(id, score, Double::sum);

                // 保留原始对象的引用（用于获取元数据等）
                idToResult.putIfAbsent(id, res);
            }
        }

        // 3. 排序、截断并返回
        return rrfScores.entrySet().stream()
                // 按 RRF 分数从高到低排序
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorSearchResult original = idToResult.get(entry.getKey());
                    // 关键点：建议创建新对象或拷贝，避免修改输入参数中的原始对象
                    return createResultWithNewScore(original, entry.getValue());
                })
                .collect(Collectors.toList());
    }

    /**
     * 辅助方法：创建一个带有新分数的结果对象（防御性拷贝）
     */
    private VectorSearchResult createResultWithNewScore(VectorSearchResult original, double newScore) {
        if (original == null) {
            return null;
        }
        VectorSearchResult copy = new VectorSearchResult();
        copy.setId(original.getId());
        copy.setVectorId(original.getVectorId());
        copy.setContent(original.getContent());
        copy.setMetadata(original.getMetadata());
        copy.setScore(newScore);
        copy.setDistance(original.getDistance());
        return copy;
    }

    @Override
    public RagResult chat(String query, String kbId, String llmModelId, List<ChatMessage> history) {
        // 先获取知识库，避免重复查询
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbId);
        }

        List<VectorSearchResult> searchResults = retrieve(query, kbId, null, null);

        String context =
                searchResults.stream().map(VectorSearchResult::getContent).collect(Collectors.joining("\n\n"));
        String templateContent = loadPromptTemplate(kb.getCategory());

        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", query);

        Prompt prompt = PromptTemplate.from(templateContent).apply(variables);

        // 使用Factory获取模型(OpenAI/Ollama等)
        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(prompt.toUserMessage());

        AgentGraph graph = agentGraphProvider.getGraph();

        AgentState state = new AgentState();
        state.setLlmModelId(llmModelId);
        state.setKbId(kbId);
        state.setMessages(messages);

        state = graph.invoke(state);

        String answer = "No response";
        int pTokens = AgentUtils.asInt(state.getAttributes().get("promptTokens"));
        int cTokens = AgentUtils.asInt(state.getAttributes().get("completionTokens"));

        if (!state.getMessages().isEmpty()) {
            ChatMessage lastMsg = state.getMessages().getLast();
            if (lastMsg instanceof AiMessage) {
                answer = ((AiMessage) lastMsg).text();
            }
        }

        return RagResult.builder()
                .answer(answer)
                .retrievedChunks(searchResults)
                .prompt(prompt.text())
                .promptTokens(pTokens)
                .completionTokens(cTokens)
                .build();
    }

    @Override
    public void streamChat(
            String query,
            String kbId,
            List<VectorSearchResult> retrievedChunks,
            String llmModelId,
            List<ChatMessage> history,
            StreamingChatResponseHandler handler) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbId);
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

        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(prompt.toUserMessage());

        AgentGraph graph = agentGraphProvider.getGraph();

        AgentState state = new AgentState();
        state.setLlmModelId(llmModelId);
        state.setKbId(kbId);
        state.setMessages(messages);
        state.getAttributes().put("streamHandler", handler);

        state = graph.invoke(state);

        if (state.getMessages().isEmpty()) {
            handler.onError(new RuntimeException("Graph无响应状态"));
        }
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
                .eq(PromptTemplateEntity::getId, "system_rag_default")
                .eq(PromptTemplateEntity::getEnabled, true)
                .last("LIMIT 1"));

        if (template != null) {
            return template.getTemplateContent();
        }

        // 3. 最后保底：硬编码模板（不推荐，但为容错提供）
        return "基于以下背景回答问题:\n{{context}}\n\n问题: {{question}}";
    }
}
