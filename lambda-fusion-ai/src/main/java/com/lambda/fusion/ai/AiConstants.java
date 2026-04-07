package com.lambda.fusion.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

public interface AiConstants {


    /**
     * 默认历史消息限制数量（用于 LLM 上下文）
     */
    int DEFAULT_HISTORY_LIMIT = 10;

    @SuppressWarnings("unused")
    interface Enums {

        /**
         * 文本分段策略枚举
         *
         * @author Jin
         */
        @Getter
        @AllArgsConstructor
        enum ChunkStrategy {

            /**
             * 固定长度分段
             */
            FIXED("固定长度"),

            /**
             * 按段落分段
             */
            PARAGRAPH("按段落"),

            /**
             * 按句子分段
             */
            SENTENCE("按句子"),

            /**
             * 滑动窗口分段
             */
            SLIDING_WINDOW("滑动窗口");

            private final String description;
        }

        /**
         * 文档处理状态枚举
         *
         * @author Jin
         */
        @Getter
        @AllArgsConstructor
        enum DocumentStatus {

            /**
             * 待处理
             */
            PENDING("待处理"),

            /**
             * 处理中
             */
            PROCESSING("处理中"),

            /**
             * 已完成
             */
            COMPLETED("已完成"),

            /**
             * 处理失败
             */
            FAILED("失败");

            private final String description;
        }

        /**
         * 会话状态枚举
         *
         * @author Jin
         */
        @Getter
        @AllArgsConstructor
        enum SessionStatus {

            /**
             * 活跃
             */
            ACTIVE("活跃"),

            /**
             * 已归档
             */
            ARCHIVED("已归档"),

            /**
             * 已删除
             */
            DELETED("已删除");

            private final String description;
        }
    }

    /**
     * LLM提供商枚举
     *
     * @author Jin
     */
    @Getter
    @AllArgsConstructor
    enum LlmProvider {

        /**
         * OpenAI
         */
        OPENAI("OpenAI"),

        /**
         * Ollama (本地部署)
         */
        OLLAMA("Ollama"),

        /**
         * Azure OpenAI
         */
        AZURE_OPENAI("Azure OpenAI"),

        /**
         * Anthropic Claude
         */
        ANTHROPIC("Anthropic");

        private final String description;
    }

    /**
     * 模型类型枚举
     *
     * @author Jin
     */
    @Getter
    @AllArgsConstructor
    enum ModelType {

        /**
         * 对话模型，支持多轮上下文交互（聊天/问答/补全统一接口）
         */
        CHAT("对话模型"),

        /**
         * 文本向量化模型，用于语义搜索和RAG检索
         */
        EMBEDDING("Embedding模型"),

        /**
         * 视觉模型，支持图像理解和分析
         */
        VISION("视觉模型"),

        /**
         * 图像生成模型，根据文本描述生成图像
         */
        IMAGE("图像生成模型"),

        /**
         * 多模态模型，支持文本+图像+音频等多种输入
         */
        MULTIMODAL("多模态模型");

        private final String description;
    }
}
