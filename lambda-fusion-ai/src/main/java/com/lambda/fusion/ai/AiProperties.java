package com.lambda.fusion.ai;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Slf4j
@ConfigurationProperties(prefix = "lambda.fusion.ai")
public class AiProperties {

    private EmbeddingConfig embedding = new EmbeddingConfig();
    private DocumentChunkConfig documentChunk = new DocumentChunkConfig();
    private AiDataSource dataSource = new AiDataSource();
    private AgentConfig agent = new AgentConfig();

    @PostConstruct
    public void validateConfiguration() {
        documentChunk.validateConfiguration();
    }

    @Data
    public static class EmbeddingConfig {
        private String provider = "openai";
        private String apiKey;
        private String modelName = "text-embedding-3-small";
        private String baseUrl;
        private Integer dimension = 1536;
    }

    private DocumentConfig document = new DocumentConfig();

    @Data
    public static class DocumentConfig {
        /**
         * OSS客户端名称，用于文档处理时从OSS加载文件
         */
        private String ossClientName = "default";
    }

    @Data
    public static class AiDataSource {

        private Boolean enabled = true;

        private String name = "ai-postgres";

        private String tenantPrefix = "ai-tenant-";
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
         * 向量批量插入的最大批次大小
         * 防止SQL语句过长导致数据库错误
         */
        @Min(value = 50, message = "向量批量插入批次大小至少为50")
        @Max(value = 500, message = "向量批量插入批次大小不能超过500")
        private int vectorBatchSize = 200;

        /**
         * 验证配置参数，如有需要则应用修正
         * <p>
         * 此方法确保：
         * 1. 分块重叠小于分块大小
         * 2. 如果检测到无效组合，则应用修正值
         * 3. 对任何修正记录警告日志
         */
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
         * @param chunkSize         用于验证的分块大小
         * @return 经过验证的分块重叠
         */
        public int getValidatedChunkOverlap(Integer configuredOverlap, int chunkSize) {
            if (configuredOverlap == null) {
                return Math.clamp(defaultChunkOverlap, 10, chunkSize / 2);
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

    /**
     * Agent 工作流引擎配置属性
     */
    @Data
    public static class AgentConfig {

        private ParallelExecutorConfig parallelExecutor = new ParallelExecutorConfig();
        private SubgraphConfig subgraph = new SubgraphConfig();
    }

    /**
     * 并行执行器线程池配置
     */
    @Data
    public static class ParallelExecutorConfig {

        /**
         * 核心线程数
         */
        private int corePoolSize = 5;

        /**
         * 最大线程数
         */
        private int maxPoolSize = 20;

        /**
         * 任务队列容量
         */
        private int queueCapacity = 100;

        /**
         * 线程存活时间（秒）
         */
        private int keepAliveSeconds = 60;

        /**
         * 线程名前缀
         */
        private String threadNamePrefix = "agent-parallel-";
    }

    /**
     * 子图配置
     */
    @Data
    public static class SubgraphConfig {

        /**
         * 异步执行超时时间（毫秒）
         */
        private long asyncTimeout = 30000;

        /**
         * 是否启用子图定义缓存
         */
        private boolean cacheEnabled = true;

        /**
         * 缓存最大条目数
         */
        private int cacheMaxSize = 100;
    }
}
