package com.lambda.fusion.ai;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.dict.DictEnum;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface AiConstants {

    @Getter
    @AllArgsConstructor
    @DictMapper(dictName = "LLM_MODEL_TYPE", dictUsage = 0, dictDesc = "模型类型")
    enum ModelType implements DictEnum<Integer> {
        CHAT(1, "对话模型"),

        EMBEDDING(2, "嵌入模型");

        @EnumValue
        private final Integer code;

        private final String label;
    }

    /**
     * LLM 提供方类型。决定运行时构建哪种 AgentScope ChatModel 客户端。
     *
     * @author Jin
     */
    @Getter
    @AllArgsConstructor
    enum ProviderType {
        DASHSCOPE("dashscope", "通义千问 DashScope"),

        OPENAI("openai", "OpenAI 兼容"),

        OLLAMA("ollama", "Ollama 本地部署");

        private final String code;

        private final String label;

        public static ProviderType of(String code) {
            if (code == null) {
                return null;
            }
            for (ProviderType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 智能应用类型。决定 Agent 构建方式与可用能力栈。
     *
     * <p>CHAT：纯 DB 配置，无 workspace（v1）。WORKSPACE：带 per-app workspace，
     * 对齐 AgentScope harness 完整能力（技能/子agent/记忆/沙箱/自演化）。
     */
    @Getter
    @AllArgsConstructor
    @DictMapper(dictName = "AI_APP_TYPE", dictUsage = 0, dictDesc = "智能应用类型")
    enum AppType implements DictEnum<String> {
        CHAT("CHAT", "聊天型"),

        WORKSPACE("WORKSPACE", "工作空间型");

        @EnumValue
        private final String code;

        private final String label;

        public static AppType of(String code) {
            if (code == null) {
                return null;
            }
            for (AppType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 沙箱后端类型。仅 WORKSPACE 型应用有效，决定文件/shell 执行的隔离环境。
     *
     * <p>HOST：宿主文件系统（无沙箱，无 shell）。DOCKER/KUBERNETES/E2B/DAYTONA/AGENTRUN：
     * 对应 AgentScope 沙箱后端，启用 shell 工具与隔离。
     */
    @Getter
    @AllArgsConstructor
    enum SandboxBackend {
        HOST("HOST", "宿主文件系统"),

        DOCKER("DOCKER", "Docker 沙箱"),

        KUBERNETES("KUBERNETES", "Kubernetes 沙箱"),

        E2B("E2B", "E2B 云沙箱"),

        DAYTONA("DAYTONA", "Daytona 云沙箱"),

        AGENTRUN("AGENTRUN", "AgentRun 云沙箱");

        private final String code;

        private final String label;

        public static SandboxBackend of(String code) {
            if (code == null) {
                return null;
            }
            for (SandboxBackend backend : values()) {
                if (backend.code.equalsIgnoreCase(code)) {
                    return backend;
                }
            }
            return null;
        }
    }

    /**
     * 子代理工作区模式。
     *
     * <p>ISOLATED（默认）：子代理在父 workspace 下的独立子目录执行，互不干扰；
     * SHARED：与主 agent 共享同一 workspace。
     */
    @Getter
    @AllArgsConstructor
    enum SubAgentWorkspaceMode {
        ISOLATED("ISOLATED", "独立工作区"),

        SHARED("SHARED", "共享工作区");

        private final String code;

        private final String label;

        public static SubAgentWorkspaceMode of(String code) {
            if (code == null) {
                return null;
            }
            for (SubAgentWorkspaceMode mode : values()) {
                if (mode.code.equalsIgnoreCase(code)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * 知识库检索模式（应用级，{@code ai_app.rag_mode}）。
     *
     * <p>GENERIC：中间件自动检索注入（RagMiddleware，稳定保底）；AGENTIC：注册
     * {@code retrieve_knowledge} 工具由模型自主检索（省 token，依赖模型工具调用能力）；
     * BOTH：两者兼得（中间件保底 + 工具可追问补查）。空值按 GENERIC 处理（向后兼容）。
     */
    @Getter
    @AllArgsConstructor
    enum RagMode {
        GENERIC("GENERIC", "自动注入"),

        AGENTIC("AGENTIC", "工具自主检索"),

        BOTH("BOTH", "注入+工具");

        private final String code;

        private final String label;

        public static RagMode of(String code) {
            if (code == null) {
                return null;
            }
            for (RagMode mode : values()) {
                if (mode.code.equalsIgnoreCase(code)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * 知识库文档入库状态。
     *
     * <p>PENDING：已落库待入库；READY：解析切块入库完成；FAILED：入库失败（原因见 error_msg）。
     */
    @Getter
    @AllArgsConstructor
    enum DocumentStatus {
        PENDING("PENDING", "待入库"),

        READY("READY", "已入库"),

        FAILED("FAILED", "入库失败");

        private final String code;

        private final String label;
    }

    /**
     * 知识库向量库后端类型。
     *
     * <p>MEMORY：进程内存（InMemoryStore，零配置，重启丢失，单节点）；PGVECTOR：
     * PostgreSQL pgvector（PgVectorStore，生产推荐，多副本共享）。
     */
    @Getter
    @AllArgsConstructor
    enum VectorStoreType {
        MEMORY("MEMORY", "进程内存(重启丢失)"),

        PGVECTOR("PGVECTOR", "PostgreSQL pgvector");

        private final String code;

        private final String label;

        public static VectorStoreType of(String code) {
            if (code == null) {
                return null;
            }
            for (VectorStoreType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Agent 状态存储类型（多轮记忆）。按部署形态配置：
     * MEMORY/FILE 单节点；MYSQL/POSTGRES/REDIS 分布式（多副本共享）；OSS/COS 对象存储归档。
     */
    @Getter
    @AllArgsConstructor
    enum StateStoreType {
        MEMORY("MEMORY", "进程内（重启丢失）"),

        FILE("FILE", "JSON 落盘（单节点，重启不丢）"),

        MYSQL("MYSQL", "MySQL（分布式）"),

        POSTGRES("POSTGRES", "PostgreSQL（分布式）"),

        REDIS("REDIS", "Redis（分布式）"),

        OSS("OSS", "对象存储 OSS/S3"),

        COS("COS", "腾讯云 COS");

        private final String code;

        private final String label;

        public static StateStoreType of(String code) {
            if (code == null) {
                return null;
            }
            for (StateStoreType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** 对话业务 Run 状态。 */
    public enum ChatRunStatus {
        CREATED,
        RUNNING,
        AWAITING_CONFIRM,
        STOPPING,
        COMPLETED,
        STOPPED,
        FAILED;

        private static final Set<ChatRunStatus> TERMINAL = Set.of(COMPLETED, STOPPED, FAILED);

        private static final List<String> TERMINAL_NAMES =
                TERMINAL.stream().map(Enum::name).toList();

        public boolean isTerminal() {
            return TERMINAL.contains(this);
        }

        public static boolean isTerminal(String status) {
            if (status == null) {
                return false;
            }
            try {
                return valueOf(status).isTerminal();
            } catch (IllegalArgumentException invalidStatus) {
                return false;
            }
        }

        /** 终态状态名列表，供 SQL in/notIn 条件复用，避免各处手写终态三元组。 */
        public static List<String> terminalNames() {
            return TERMINAL_NAMES;
        }
    }

    /** 对话运行结束原因，持久化时写入 finish_reason 列。 */
    public enum ChatRunFinishReason {
        SUCCESS,
        ERROR,
        USER_STOP,
        CONFIRM_TIMEOUT
    }

    /** 对话运行失败码，持久化时写入 error_code 列。 */
    public enum ChatRunFailureCode {
        ERROR,
        START_FAILED,
        STATE_CONFLICT,
        INSTANCE_LOST,
        RUN_CAPACITY_EXCEEDED,
        CONFIRM_CONTEXT_UNAVAILABLE
    }

    /** 工具调用状态，持久化字面量为小写 code。 */
    @Getter
    @AllArgsConstructor
    enum ChatRunToolStatus {
        RUNNING("running"),
        COMPLETE("complete"),
        ASKING("asking"),
        UNKNOWN("unknown");

        private final String code;
    }
}
