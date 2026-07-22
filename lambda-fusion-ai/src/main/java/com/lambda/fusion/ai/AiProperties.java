package com.lambda.fusion.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置。
 * <p>
 * 模型管理采用数据库驱动：提供方与模型配置持久化在 {@code ai_llm_provider} /
 * {@code ai_llm_model} 表中（API Key 加密存储），由
 * {@code AiModelResolver} 在运行时按 modelId 解析为 AgentScope {@code Model}。
 * 本配置类仅保留运行时参数与安全配置，不承载模型连接信息。
 *
 * @author Jin
 */
@Data
@Slf4j
@ConfigurationProperties(prefix = "lambda.fusion.ai")
public class AiProperties {

    private Runtime runtime = new Runtime();

    /**
     * 安全配置（LLM API Key 加密）。
     */
    private Security security = new Security();

    /**
     * Workspace 配置（WORKSPACE 型应用的文件系统根）。
     */
    private Workspace workspace = new Workspace();

    /**
     * 沙箱配置（WORKSPACE 型应用的执行隔离）。
     */
    private Sandbox sandbox = new Sandbox();

    /**
     * 受众配置（B/C 端角色映射，用于应用可见性）。
     */
    private Audience audience = new Audience();

    @Data
    public static class Runtime {

        /**
         * 单次 ReAct 循环最大迭代数（应用未配置时回退）。
         */
        private int defaultMaxIters = 10;
    }

    @Data
    public static class Security {

        /**
         * AES 加密密钥（用于加密 LLM API Key），生产环境必须配置；未配置时启动会失败。
         */
        private String encryptionKey;
    }

    @Data
    public static class Workspace {

        /**
         * workspace 根目录。每个应用 workspace 位于
         * {@code {root}/tenants/{tenantId}/apps/{appId}/}。默认 {@code ${user.home}/.agentscope/fusion}。
         */
        private String root;
    }

    @Data
    public static class Sandbox {

        /**
         * 隔离粒度：AGENT（默认，app 级共享）/ USER / SESSION / GLOBAL。
         */
        private String isolationScope = "AGENT";

        private Docker docker = new Docker();

        private Kubernetes kubernetes = new Kubernetes();

        private E2b e2b = new E2b();

        private Daytona daytona = new Daytona();

        private AgentRun agentRun = new AgentRun();

        @Data
        public static class Docker {
            private String image = "agentscope/python-sandbox:py311-slim";
            private String network = "none";
            private Long cpuCount;
            private Long memorySizeBytes;
            private String workspaceRoot = "/workspace";
        }

        @Data
        public static class Kubernetes {
            private String masterUrl;
            private String namespace = "default";
            private String image;
            private String workspaceRoot = "/workspace";
            private String serviceAccount;
            private String token;
        }

        @Data
        public static class E2b {
            private String apiKey;
            private String templateId;
            private String domain;
            private String workspaceRoot = "/workspace";
        }

        @Data
        public static class Daytona {
            private String apiKey;
            private String apiUrl;
            private String workspaceRoot = "/workspace";
        }

        @Data
        public static class AgentRun {
            private String apiKey;
            private String apiUrl;
            private String workspaceRoot = "/workspace";
        }
    }

    /**
     * 受众配置：B/C 端角色名列表，用于应用可见性过滤。
     */
    @Data
    public static class Audience {

        /**
         * B 端角色名列表（命中即视为 B 端用户，可见 audience=B 的应用）。
         */
        private List<String> bRoles = new ArrayList<>();

        /**
         * C 端角色名列表。
         */
        private List<String> cRoles = new ArrayList<>();
    }
}
