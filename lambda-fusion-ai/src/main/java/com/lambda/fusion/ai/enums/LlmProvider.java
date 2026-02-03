package com.lambda.fusion.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LLM提供商枚举
 *
 * @author Jin
 */
@Getter
@AllArgsConstructor
public enum LlmProvider {

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
