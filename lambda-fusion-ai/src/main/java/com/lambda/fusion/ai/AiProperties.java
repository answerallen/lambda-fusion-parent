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

    /**
     * Gateway 配置（AgentScope harness Gateway + 外部通道 SPI）。
     */
    private Gateway gateway = new Gateway();

    /**
     * Agent 状态存储配置（多轮记忆）。按部署形态选择。
     */
    private StateStore stateStore = new StateStore();

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

    // Gateway 配置：是否启用 AgentScope harness Gateway 作为运行时入口
    @Data
    public static class Gateway {

        // 是否启用 Gateway（默认 true）。关闭时回退直连 {@code HarnessAgent.streamEvents}
        private boolean enabled = true;
    }

    /**
     * Agent 状态存储配置（多轮记忆）。按部署形态选择后端：
     *
     * <ul>
     *   <li>{@code MEMORY}（默认，进程内，重启丢失）/ {@code FILE}（JSON 落盘，单节点，重启不丢）。</li>
     *   <li>{@code MYSQL} / {@code POSTGRES} / {@code REDIS}：分布式，多副本共享状态。</li>
     *   <li>{@code OSS} / {@code COS}：对象存储归档（分布式，低频访问）。</li>
     * </ul>
     *
     * <p>分布式后端依赖对应的 AgentScope 扩展（pom 中为 {@code optional} 依赖 + {@code @ConditionalOnClass}
     * 条件装配）。扩展或其客户端不在 classpath 时，解析器告警并回退 {@code MEMORY}，不阻塞启动。
     */
    @Data
    public static class StateStore {

        // 存储类型：MEMORY / FILE / MYSQL / POSTGRES / REDIS / OSS / COS
        private String type = "MEMORY";

        // FILE 模式根目录；默认 {@code ${workspace.root}/state}
        private String root;

        private Mysql mysql = new Mysql();

        private Postgres postgres = new Postgres();

        private Redis redis = new Redis();

        private Oss oss = new Oss();

        private Cos cos = new Cos();

        // MySQL 分布式状态存储；复用 fusion master 数据源
        @Data
        public static class Mysql {

            // dynamic-datasource 名称；默认 {@code master}
            private String datasource = "master";

            // 库名（{@code createIfNotExist} 时自动创建）；默认 {@code agentscope}
            private String database = "agentscope";

            // 表名（{@code createIfNotExist} 时自动创建）；默认 {@code agentscope_sessions}
            private String table = "agentscope_sessions";

            // 库/表不存在时是否自动创建；默认 true
            private boolean createIfNotExist = true;
        }

        // PostgreSQL 分布式状态存储；复用 ai-postgres 数据源
        @Data
        public static class Postgres {

            // dynamic-datasource 名称；默认 {@code ai-postgres}
            private String datasource = "ai-postgres";

            // schema 名（{@code createIfNotExist} 时自动创建）；默认 {@code agentscope}
            private String schema = "agentscope";

            // 表名（{@code createIfNotExist} 时自动创建）；默认 {@code agentscope_sessions}
            private String table = "agentscope_sessions";

            // schema/表不存在时是否自动创建；默认 true
            private boolean createIfNotExist = true;
        }

        // Redis 分布式状态存储；复用 RedissonClient bean（redisson-spring-boot-starter 自动装配）
        @Data
        public static class Redis {

            // Redis key 前缀；默认 {@code agentscope:session:}
            private String keyPrefix = "agentscope:session:";
        }

        // 阿里云 OSS 对象存储状态存储；需 agentscope-extensions-oss 扩展
        @Data
        public static class Oss {

            private String endpoint;

            private String accessKeyId;

            private String accessKeySecret;

            private String bucketName;

            // 对象 key 前缀；默认 {@code agentscope/state/}
            private String keyPrefix = "agentscope/state/";
        }

        // 腾讯云 COS 对象存储状态存储；需 agentscope-extensions-cos 扩展
        @Data
        public static class Cos {

            // 地域（如 {@code ap-guangzhou}）；COS 按地域寻址
            private String region;

            private String secretId;

            private String secretKey;

            private String bucketName;

            // 对象 key 前缀；默认 {@code agentscope/state/}
            private String keyPrefix = "agentscope/state/";
        }
    }
}
