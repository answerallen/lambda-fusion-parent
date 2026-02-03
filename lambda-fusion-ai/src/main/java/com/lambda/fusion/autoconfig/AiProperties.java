package com.lambda.fusion.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lambda.fusion.ai")
public class AiProperties {

    private DocumentConfig document = new DocumentConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private ChatConfig chat = new ChatConfig(); // Added

    @Data
    public static class DocumentConfig {
        private String storageType = "LOCAL";
        private String basePath = "/data/ai-documents";
        private Long maxFileSize = 10 * 1024 * 1024L;
    }

    @Data
    public static class EmbeddingConfig {
        private String provider = "openai";
        private String apiKey;
        private String modelName = "text-embedding-3-small";
        private String baseUrl;
        private Integer dimension = 1536;
    }

    @Data // Added
    public static class ChatConfig {
        private String provider = "openai";
        private String apiKey;
        private String modelName = "gpt-4o-mini";
        private String baseUrl;
        private Double temperature = 0.7;
    }
}
