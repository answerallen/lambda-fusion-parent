package com.lambda.fusion.ai.knowledge.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagResult {
    private String answer;
    private List<VectorSearchResult> retrievedChunks;
    private String prompt;
    private Integer promptTokens;
    private Integer completionTokens;
}
