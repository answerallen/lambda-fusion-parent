package com.lambda.fusion.ai.model.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagResult {
    private String answer;
    private List<VectorSearchResultDTO> retrievedChunks;
    private String prompt;
    private Integer promptTokens;
    private Integer completionTokens;
}
