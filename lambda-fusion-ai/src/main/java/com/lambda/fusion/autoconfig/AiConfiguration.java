package com.lambda.fusion.autoconfig;

import com.lambda.autoconfig.SecurityAutoConfiguration;
import com.lambda.fusion.ai.AiConfigure;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@Import(AiConfigure.class)
@EnableAsync
@EnableConfigurationProperties({AiProperties.class})
public class AiConfiguration {

    @Bean
    public EmbeddingModel embeddingModel(AiProperties aiProperties) {
        AiProperties.EmbeddingConfig config = aiProperties.getEmbedding();

        return OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Bean
    public ChatModel chatLanguageModel(AiProperties aiProperties) {
        AiProperties.ChatConfig config = aiProperties.getChat();

        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature())
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatLanguageModel(AiProperties aiProperties) {
        AiProperties.ChatConfig config = aiProperties.getChat();

        return OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature())
                .build();
    }
}
