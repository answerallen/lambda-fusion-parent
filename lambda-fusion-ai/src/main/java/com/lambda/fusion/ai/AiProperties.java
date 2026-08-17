package com.lambda.fusion.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
@Validated
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

    /**
     * 技能市场配置（技能仓库源选择）。
     */
    private Skill skill = new Skill();

    /**
     * 知识库（RAG）配置（对话检索注入与 pgvector 向量库连接）。
     */
    @Valid
    private Rag rag = new Rag();

    /**
     * 对话配置（附件上传等）。
     */
    @Valid
    private Chat chat = new Chat();

    private Cluster cluster = new Cluster();

    /**
     * 对话配置。
     */
    @Data
    public static class Chat {

        /** 对话附件配置。 */
        private Attachment attachment = new Attachment();

        /** 可恢复后台对话 Run。 */
        @Valid
        private Run run = new Run();

        @Data
        public static class Run {

            @Min(1)
            private long connectionTimeoutSeconds = 300;

            @Min(1)
            private long maxRunDurationSeconds = 1800;

            @Min(1)
            private long awaitConfirmTimeoutSeconds = 86400;

            @Min(1)
            @Max(300)
            private long stopGraceSeconds = 10;

            @Min(1)
            private long terminalTtlSeconds = 600;

            @Min(64)
            private int maxEvents = 4096;

            @Min(65536)
            private long maxBytes = 8_388_608;

            @Min(1)
            private int maxActiveRuns = 200;

            @Min(1)
            private int maxActiveRunsPerUser = 4;

            @Min(1)
            private int subscriberQueueSize = 256;

            @Min(1)
            private int snapshotEveryEvents = 100;

            @Min(1)
            private long snapshotIntervalSeconds = 2;
        }

        /**
         * 对话附件配置。原文件存储复用 {@code rag.document-storage}（LOCAL/OSS），此处仅承载
         * 附件自身的校验与注入约束。
         */
        @Data
        public static class Attachment {

            /** 单文件大小上限(MB)；默认 10。需与 spring.servlet.multipart.max-file-size 对齐。 */
            private long maxFileSizeMb = 10;

            /** 单条消息附件数上限；默认 5。 */
            private int maxCount = 5;

            /** 文档附件抽取文本拼接进 prompt 的最大字符数，防上下文膨胀；默认 8000。 */
            private int maxExtractChars = 8000;

            /** 图片扩展名白名单（小写）。 */
            private List<String> imageTypes = List.of("jpg", "jpeg", "png", "gif", "webp");

            /** 文档扩展名白名单（小写）。 */
            private List<String> docTypes = List.of("pdf", "doc", "docx", "txt", "md");

            /** 图片预览直链配置。 */
            private Preview preview = new Preview();

            /**
             * 图片预览直链配置：为图片附件签发带 HMAC 签名的预览 URL，使 {@code <img src>} 无需 Bearer
             * 即可访问（preview 端点放行登录，靠签名 token 鉴权；token 绑定 attachmentId 且有时效）。
             */
            @Data
            public static class Preview {

                /**
                 * HMAC-SHA256 签名密钥；生产必须经环境变量注入
                 * （{@code AI_ATTACHMENT_PREVIEW_SECRET}）。未配置时图片预览直链不可用，降级为文件名展示。
                 */
                private String secret;

                /** token 有效期（秒）；默认 3600。 */
                private long ttlSeconds = 3600;
            }
        }
    }

    @Data
    public static class Runtime {

        /**
         * 应用未配置 maxIters 时使用的 ReAct 最大迭代次数。
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
         * workspace 根目录。本地应用位于 {@code {root}/tenants/{tenantId}/apps/{appId}/}；远程存储的
         * 初始化模板位于 {@code {root}/.remote-templates/{type}/tenants/{tenantId}/apps/{appId}/}。默认
         * {@code ${user.home}/.agentscope/fusion}。
         */
        private String root;

        /** Workspace 存储配置。存储类型是部署级属性，所有应用统一使用。 */
        @Valid
        private Storage storage = new Storage();

        @Data
        public static class Storage {

            /** 存储类型：LOCAL（默认）/ MYSQL / POSTGRES。 */
            private String type = "LOCAL";

            @Valid
            private Mysql mysql = new Mysql();

            @Valid
            private Postgres postgres = new Postgres();

            @Data
            public static class Mysql {

                /** dynamic-datasource 名称；默认复用 master。 */
                private String datasource = "master";
            }

            @Data
            public static class Postgres {

                /** dynamic-datasource 名称；默认复用 ai-postgres。 */
                private String datasource = "ai-postgres";
            }
        }
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

    @Data
    public static class Gateway {

        /** 是否通过 HarnessGateway 路由对话流；关闭时直连 HarnessAgent#streamEvents。 */
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

        /** 状态存储后端：MEMORY / FILE / MYSQL / POSTGRES / REDIS / OSS / COS。 */
        private String type = "MEMORY";

        /** FILE 模式状态目录；默认使用 workspace.root/state。 */
        private String root;

        private Mysql mysql = new Mysql();

        private Postgres postgres = new Postgres();

        private Redis redis = new Redis();

        private Oss oss = new Oss();

        private Cos cos = new Cos();

        /** MySQL 状态存储配置，默认复用 master 数据源。 */
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

        /** PostgreSQL 状态存储配置，默认复用 ai-postgres 数据源。 */
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

    /**
     * 技能市场配置：技能仓库源按部署形态选择，复用 AgentScope 已有仓库实现。
     *
     * <p>MYSQL/POSTGRES：可读写（admin CRUD 经仓库 API）。GIT/NACOS：通常只读 catalog。
     * 扩展未引入或 {@code type=NONE} 时技能市场禁用（WORKSPACE app 仅用 workspace 本地技能）。
     */
    @Data
    public static class Skill {

        private Repository repository = new Repository();

        @Data
        public static class Repository {

            /** 源类型：MYSQL / POSTGRES / GIT / NACOS / NONE（默认 MYSQL）。 */
            private String type = "MYSQL";

            private Mysql mysql = new Mysql();

            private Postgres postgres = new Postgres();

            private Git git = new Git();

            private Nacos nacos = new Nacos();

            /** MySQL 技能仓库（复用 MysqlSkillRepository，默认数据源 master）。 */
            @Data
            public static class Mysql {

                /** dynamic-datasource 名称；默认 {@code master}。 */
                private String datasource = "master";

                /** 库/表不存在时自动创建；默认 true。 */
                private boolean createIfNotExist = true;

                /** 是否可写（admin CRUD）；默认 true。 */
                private boolean writeable = true;
            }

            /** PostgreSQL 技能仓库（复用 PostgresSkillRepository，默认数据源 ai-postgres）。 */
            @Data
            public static class Postgres {

                /** dynamic-datasource 名称；默认 {@code ai-postgres}。 */
                private String datasource = "ai-postgres";

                /** schema/表不存在时自动创建；默认 true。 */
                private boolean createIfNotExist = true;

                /** 是否可写；默认 true。 */
                private boolean writeable = true;
            }

            /** Git 技能仓库（复用 GitSkillRepository，只读 catalog）。 */
            @Data
            public static class Git {

                private String remoteUrl;

                private String branch;

                /** 本地克隆目录；默认 {@code ${workspace.root}/skill-git}。 */
                private String localPath;

                private String source;
            }

            /** Nacos 技能仓库（复用 NacosSkillRepository）。 */
            @Data
            public static class Nacos {

                private String serverAddr;

                private String namespaceId;

                private String accessKey;

                private String secretKey;
            }
        }
    }

    /**
     * 知识库（RAG）配置。
     *
     * <p>{@code enabled=false}（默认）时检索注入整组不装配，{@code AgentFactory} 判空跳过。
     * 向量库默认 MEMORY（进程内，重启丢失，零配置即可体验）；生产建议 PGVECTOR——
     * {@code PgVectorStore} 只接受 JDBC 连接串（不支持注入 DataSource），故连接信息走本配置
     * 而非复用 {@code ai-postgres} 动态数据源。
     */
    @Data
    public static class Rag {

        /** 是否启用知识库检索注入；默认 false。 */
        private boolean enabled = false;

        /** 知识库未配置时的默认检索条数；默认 5。 */
        private int defaultLimit = 5;

        /** 默认分数阈值；默认 0.5。 */
        private double defaultScoreThreshold = 0.5;

        /** 单次注入的最大拼接字符数，防上下文膨胀；默认 4000。 */
        private int maxInjectChars = 4000;

        /** 文档切割配置。 */
        @Valid
        private Chunking chunking = new Chunking();

        /** 向量库后端。 */
        private Store store = new Store();

        /** 文档原文件存储。知识库文档与对话附件共用本组配置。 */
        private DocumentStorage documentStorage = new DocumentStorage();

        private PgVector pgVector = new PgVector();

        /** 文档切割参数；具体策略随文档持久化，参数由模块统一配置。 */
        @Data
        public static class Chunking {

            /** 段落/章节切割的目标字符数，TOKEN 策略下表示近似 token 数；默认 512。 */
            @Min(1)
            private int chunkSize = 512;

            /** 相邻切块重叠字符数，TOKEN 策略下表示近似 token 数；默认 50。 */
            @Min(0)
            private int overlapSize = 50;

            /** AUTO 策略下整篇保留的最大字符数；默认 2000。 */
            @Min(1)
            private int wholeDocumentMaxChars = 2000;

            @AssertTrue(message = "lambda.fusion.ai.rag.chunking.overlap-size 必须小于 chunk-size")
            public boolean isOverlapSizeValid() {
                return overlapSize < chunkSize;
            }
        }

        /** 向量库后端：MEMORY（默认，进程内，重启丢失）/ PGVECTOR。 */
        @Data
        public static class Store {

            private String type = "MEMORY";
        }

        /** 文档原文件存储配置。 */
        @Data
        public static class DocumentStorage {

            /** 存储类型：LOCAL（默认）/ OSS。 */
            private String type = "LOCAL";

            private Local local = new Local();

            private Oss oss = new Oss();

            @Data
            public static class Local {

                /**
                 * 原文件根目录；默认 {@code {workspace.root}/knowledge-files}，
                 * workspace.root 未配时 {@code ~/.agentscope/fusion/knowledge-files}。
                 */
                private String root;
            }

            @Data
            public static class Oss {

                /** OssClientManager 中的客户端名；空 = 默认客户端。 */
                private String clientName;

                /** 对象 key 前缀；默认 {@code ai/knowledge/}。 */
                private String keyPrefix = "ai/knowledge/";
            }
        }

        /** pgvector 向量库连接配置（环境变量驱动，如 {@code ${AI_PGVECTOR_JDBC_URL}}）。 */
        @Data
        public static class PgVector {

            private String jdbcUrl;

            private String username;

            private String password;

            /** schema 名；默认 {@code public}。 */
            private String schema = "public";
        }
    }

    @Data
    public static class Cluster {

        /**
         * 是否启用跨实例 Agent 缓存失效广播（Dubbo broadcast）。多实例部署开；单实例可关。
         * 默认开，Dubbo 不在 classpath 时由条件装配自动退化为本地事件。
         */
        private boolean invalidationBroadcast = true;
    }
}
