package com.lambda.fusion.autoconfig;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@Slf4j
@ConfigurationProperties(prefix = "lambda.fusion.ai")
public class AiProperties {

    private DocumentConfig document = new DocumentConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private ChatConfig chat = new ChatConfig(); // 已添加
    private DocumentChunkConfig documentChunk = new DocumentChunkConfig(); // 已添加

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

    @Data // 已添加
    public static class ChatConfig {
        private String provider = "openai";
        private String apiKey;
        private String modelName = "gpt-4o-mini";
        private String baseUrl;
        private Double temperature = 0.7;
    }

    @Data
    public static class DocumentChunkConfig {

        /**
         * 文档分割的默认分块大小（以token为单位）
         * 有效范围：100-2000 tokens
         */
        @Min(value = 100, message = "分块大小至少为100个token")
        @Max(value = 2000, message = "分块大小不能超过2000个token")
        private int defaultChunkSize = 500;

        /**
         * 文档分割的默认分块重叠（以token为单位）
         * 有效范围：10-500 tokens，必须小于分块大小
         */
        @Min(value = 10, message = "分块重叠至少为10个token")
        @Max(value = 500, message = "分块重叠不能超过500个token")
        private int defaultChunkOverlap = 50; // 默认分块大小的10%

        /**
         * 文档上传允许的最大文件大小（以字节为单位）
         */
        @Min(value = 1024, message = "最大文件大小至少为1KB")
        private long maxFileSize = 10 * 1024 * 1024L; // 10MB

        /**
         * 处理文档分块的批次大小
         */
        @Min(value = 10, message = "批次大小至少为10")
        @Max(value = 1000, message = "批次大小不能超过1000")
        private int batchSize = 100;

        /**
         * 在启动时验证配置参数，如有需要则应用修正
         *
         * 此方法确保：
         * 1. 分块重叠小于分块大小
         * 2. 如果检测到无效组合，则应用修正值
         * 3. 对任何修正记录警告日志
         */
        @PostConstruct
        public void validateConfiguration() {
            log.info("正在验证AI配置参数...");

            boolean configChanged = false;

            // 验证分块重叠小于分块大小
            if (defaultChunkOverlap >= defaultChunkSize) {
                int correctedOverlap = Math.max(10, defaultChunkSize / 10); // 分块大小的10%，最小为10
                log.warn(
                        "分块重叠({})应该小于分块大小({})。调整为分块大小的10%: {}",
                        defaultChunkOverlap, defaultChunkSize, correctedOverlap);
                defaultChunkOverlap = correctedOverlap;
                configChanged = true;
            }

            // 验证分块重叠是合理的（不超过分块大小的50%）
            int maxRecommendedOverlap = defaultChunkSize / 2;
            if (defaultChunkOverlap > maxRecommendedOverlap) {
                int correctedOverlap = Math.max(10, defaultChunkSize / 10); // 分块大小的10%
                log.warn(
                        "分块重叠({})超过分块大小的50%({})。调整为分块大小的10%: {}",
                        defaultChunkOverlap, defaultChunkSize, correctedOverlap);
                defaultChunkOverlap = correctedOverlap;
                configChanged = true;
            }

            if (configChanged) {
                log.warn("配置已自动修正。请更新您的配置文件以避免此警告。");
            } else {
                log.info("AI配置验证成功完成。分块大小: {}, 分块重叠: {}", defaultChunkSize, defaultChunkOverlap);
            }
        }

        /**
         * 获取经过验证的分块大小，如需要则应用默认值
         *
         * @param configuredSize 知识库配置的分块大小
         * @return 经过验证的分块大小
         */
        public int getValidatedChunkSize(Integer configuredSize) {
            if (configuredSize == null) {
                return defaultChunkSize;
            }

            // 验证范围
            if (configuredSize < 100) {
                log.warn("配置的分块大小({})太小，使用最小值: 100", configuredSize);
                return 100;
            }
            if (configuredSize > 2000) {
                log.warn("配置的分块大小({})太大，使用最大值: 2000", configuredSize);
                return 2000;
            }

            return configuredSize;
        }

        /**
         * 获取经过验证的分块重叠，确保其适合给定的分块大小
         *
         * @param configuredOverlap 知识库配置的分块重叠
         * @param chunkSize 用于验证的分块大小
         * @return 经过验证的分块重叠
         */
        public int getValidatedChunkOverlap(Integer configuredOverlap, int chunkSize) {
            if (configuredOverlap == null) {
                // 使用分块大小的10%作为默认值，最小为10
                return Math.max(10, chunkSize / 10);
            }

            // 验证范围
            if (configuredOverlap < 10) {
                log.warn("配置的分块重叠({})太小，使用最小值: 10", configuredOverlap);
                return 10;
            }

            // 确保重叠小于分块大小
            if (configuredOverlap >= chunkSize) {
                int correctedOverlap = Math.max(10, chunkSize / 10);
                log.warn("配置的分块重叠({})不小于分块大小({})，使用分块大小的10%: {}", configuredOverlap, chunkSize, correctedOverlap);
                return correctedOverlap;
            }

            // 如果重叠超过分块大小的50%则警告（不常见但不无效）
            if (configuredOverlap > chunkSize / 2) {
                log.warn("配置的分块重叠({})超过分块大小的50%({})。这可能导致过度重叠。", configuredOverlap, chunkSize);
            }

            return configuredOverlap;
        }
    }
}
