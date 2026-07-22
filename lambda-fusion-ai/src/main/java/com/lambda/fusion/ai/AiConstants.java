package com.lambda.fusion.ai;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lambda.fusion.core.annotation.DictMapper;
import com.lambda.fusion.core.dict.DictEnum;
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
     *
     * @author Jin
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
     *
     * @author Jin
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
     * Agent 状态存储类型（多轮记忆）。按部署形态配置：
     * MEMORY/FILE 单节点；MYSQL/POSTGRES/REDIS 分布式（多副本共享）；OSS/COS 对象存储归档。
     *
     * @author Jin
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
}
