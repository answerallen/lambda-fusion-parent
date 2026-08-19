package com.lambda.fusion.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.fusion.ai.AiConstants.WorkspaceStorageType;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import com.lambda.fusion.ai.rag.mapper.KnowledgeDocumentMapper;
import com.lambda.fusion.ai.rag.runtime.SimpleKnowledgeAdapter;
import com.lambda.fusion.ai.rag.service.DocumentIngestionService;
import com.lambda.fusion.ai.rag.service.KnowledgeBaseService;
import com.lambda.fusion.ai.rag.storage.DocumentFileStorageResolver;
import com.lambda.fusion.ai.rag.storage.LocalDocumentFileStorage;
import com.lambda.fusion.ai.rag.storage.OssDocumentFileStorage;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.EmbeddingModelResolver;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.runtime.event.DubboConfigInvalidationBroadcaster;
import com.lambda.fusion.ai.runtime.event.RemoteAgentCacheInvalidationService;
import com.lambda.fusion.ai.runtime.gateway.ChannelBootstrap;
import com.lambda.fusion.ai.runtime.gateway.ChannelConfigApplier;
import com.lambda.fusion.ai.runtime.gateway.ChannelLifecycle;
import com.lambda.fusion.ai.runtime.gateway.FusionSubagentGateway;
import com.lambda.fusion.ai.runtime.sandbox.SandboxBackendProvider;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.state.StateStoreDataSources;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceDistributedStoreProvider;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceStorage;
import com.lambda.fusion.ai.skill.SkillRepositoryProvider;
import com.lambda.fusion.authority.api.RemoteUserService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.channel.dingtalk.DingTalkChannel;
import io.agentscope.extensions.channel.feishu.FeishuCallbackController;
import io.agentscope.extensions.channel.feishu.FeishuChannel;
import io.agentscope.extensions.channel.wecom.WeComCallbackController;
import io.agentscope.extensions.channel.wecom.WeComChannel;
import io.agentscope.extensions.cos.CosAgentStateStore;
import io.agentscope.extensions.mysql.MysqlDistributedStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.oss.OssAgentStateStore;
import io.agentscope.extensions.postgresql.PostgresDistributedStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec;
import io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.InMemorySubagentRegistry;
import io.agentscope.harness.agent.gateway.StoreBackedSubagentRegistry;
import io.agentscope.harness.agent.gateway.SubagentRegistry;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelFactory;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.annotation.DubboService;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.ai.**.mapper"})
@ComponentScan(basePackageClasses = AiConfigure.class)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfigure {

    @Configuration
    @ConditionalOnProperty(
            prefix = "lambda.fusion.ai.gateway",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class GatewayConfiguration {

        @Bean
        public ChannelManager channelManager() {
            return new ChannelManager();
        }

        @Bean
        public SubagentRegistry fusionSubagentRegistry(WorkspaceStorage workspaceStorage) {
            return workspaceStorage
                    .distributedStore()
                    .<SubagentRegistry>map(store -> new StoreBackedSubagentRegistry(store.baseStore()))
                    .orElseGet(InMemorySubagentRegistry::new);
        }

        @Bean
        public FusionSubagentGateway fusionSubagentGateway(
                ObjectProvider<AgentFactory> agentFactoryProvider,
                WorkspaceStorage workspaceStorage,
                SubagentRegistry subagentRegistry) {
            return new FusionSubagentGateway(agentFactoryProvider, workspaceStorage, subagentRegistry);
        }

        @Bean
        public HarnessGateway fusionGateway(ChannelManager channelManager) {
            return HarnessGateway.create(channelManager);
        }

        @Bean
        public ChannelLifecycle channelLifecycle(
                ChannelManager channelManager,
                HarnessGateway gateway,
                List<Channel> channels,
                ChannelConfigApplier channelConfigApplier) {
            return new ChannelLifecycle(channelManager, gateway, channels, channelConfigApplier);
        }

        @Bean
        public ChannelBootstrap channelBootstrap(
                ChannelManager channelManager,
                HarnessGateway gateway,
                ChannelConfigService channelConfigService,
                Map<String, ChannelFactory> factories) {
            return new ChannelBootstrap(channelManager, gateway, channelConfigService, factories);
        }
    }

    @Configuration
    public static class SandboxConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec")
        @RequiredArgsConstructor
        public static class AgentRunSandboxConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SandboxBackendProvider agentRunSandboxProvider() {
                return new SandboxBackendProvider() {
                    @Override
                    public AiConstants.SandboxBackend backend() {
                        return AiConstants.SandboxBackend.AGENTRUN;
                    }

                    @Override
                    public SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace) {
                        AiProperties.Sandbox.AgentRun cfg =
                                aiProperties.getSandbox().getAgentRun();
                        return new AgentRunFilesystemSpec()
                                .apiKey(cfg.getApiKey())
                                .workspaceRoot(cfg.getWorkspaceRoot())
                                .isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties));
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec")
        @RequiredArgsConstructor
        public static class DaytonaSandboxConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SandboxBackendProvider daytonaSandboxProvider() {
                return new SandboxBackendProvider() {
                    @Override
                    public AiConstants.SandboxBackend backend() {
                        return AiConstants.SandboxBackend.DAYTONA;
                    }

                    @Override
                    public SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace) {
                        AiProperties.Sandbox.Daytona cfg =
                                aiProperties.getSandbox().getDaytona();
                        return new DaytonaFilesystemSpec()
                                .apiKey(cfg.getApiKey())
                                .workspaceRoot(cfg.getWorkspaceRoot())
                                .isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties));
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec")
        @RequiredArgsConstructor
        public static class DockerSandboxConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SandboxBackendProvider dockerSandboxProvider() {
                return new SandboxBackendProvider() {
                    @Override
                    public AiConstants.SandboxBackend backend() {
                        return AiConstants.SandboxBackend.DOCKER;
                    }

                    @Override
                    public SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace) {
                        AiProperties.Sandbox.Docker cfg =
                                aiProperties.getSandbox().getDocker();
                        DockerFilesystemSpec spec = new DockerFilesystemSpec()
                                .image(cfg.getImage())
                                .workspaceRoot(cfg.getWorkspaceRoot())
                                .network(cfg.getNetwork());
                        if (cfg.getCpuCount() != null) {
                            spec.cpuCount(cfg.getCpuCount());
                        }
                        if (cfg.getMemorySizeBytes() != null) {
                            spec.memorySizeBytes(cfg.getMemorySizeBytes());
                        }
                        return spec.isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties));
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec")
        @RequiredArgsConstructor
        public static class E2bSandboxConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SandboxBackendProvider e2bSandboxProvider() {
                return new SandboxBackendProvider() {
                    @Override
                    public AiConstants.SandboxBackend backend() {
                        return AiConstants.SandboxBackend.E2B;
                    }

                    @Override
                    public SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace) {
                        AiProperties.Sandbox.E2b cfg = aiProperties.getSandbox().getE2b();
                        return new E2bFilesystemSpec()
                                .apiKey(cfg.getApiKey())
                                .templateId(cfg.getTemplateId())
                                .domain(cfg.getDomain())
                                .workspaceRoot(cfg.getWorkspaceRoot())
                                .isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties));
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec")
        @RequiredArgsConstructor
        public static class KubernetesSandboxConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SandboxBackendProvider kubernetesSandboxProvider() {
                return new SandboxBackendProvider() {
                    @Override
                    public AiConstants.SandboxBackend backend() {
                        return AiConstants.SandboxBackend.KUBERNETES;
                    }

                    @Override
                    public SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace) {
                        AiProperties.Sandbox.Kubernetes cfg =
                                aiProperties.getSandbox().getKubernetes();
                        return new KubernetesFilesystemSpec()
                                .namespace(cfg.getNamespace())
                                .image(cfg.getImage())
                                .workspaceRoot(cfg.getWorkspaceRoot())
                                .kubernetesClient(new KubernetesClientBuilder().build())
                                .isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties));
                    }
                };
            }
        }
    }

    /** Workspace 分布式存储后端装配。LOCAL 模式不创建外部存储连接。 */
    @Configuration
    public static class WorkspaceStorageConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.mysql.MysqlDistributedStore")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class MysqlWorkspaceStorageConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public WorkspaceDistributedStoreProvider mysqlWorkspaceDistributedStoreProvider() {
                return new WorkspaceDistributedStoreProvider() {
                    @Override
                    public WorkspaceStorageType type() {
                        return WorkspaceStorageType.MYSQL;
                    }

                    @Override
                    public io.agentscope.harness.agent.DistributedStore create() {
                        AiProperties.Workspace.Storage.Mysql cfg =
                                aiProperties.getWorkspace().getStorage().getMysql();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return MysqlDistributedStore.create(ds);
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.postgresql.PostgresDistributedStore")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class PostgresWorkspaceStorageConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public WorkspaceDistributedStoreProvider postgresWorkspaceDistributedStoreProvider() {
                return new WorkspaceDistributedStoreProvider() {
                    @Override
                    public WorkspaceStorageType type() {
                        return WorkspaceStorageType.POSTGRES;
                    }

                    @Override
                    public io.agentscope.harness.agent.DistributedStore create() {
                        AiProperties.Workspace.Storage.Postgres cfg =
                                aiProperties.getWorkspace().getStorage().getPostgres();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return PostgresDistributedStore.create(ds);
                    }
                };
            }
        }
    }

    /**
     * 分布式状态存储后端装配
     */
    @Configuration
    public static class StateStoreConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.postgresql.state.PostgresAgentStateStore")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class PostgresStateStoreConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public StateStoreProvider postgresStateStoreProvider() {
                return new StateStoreProvider() {
                    @Override
                    public AiConstants.StateStoreType type() {
                        return AiConstants.StateStoreType.POSTGRES;
                    }

                    @Override
                    public AgentStateStore create() {
                        AiProperties.StateStore.Postgres cfg =
                                aiProperties.getStateStore().getPostgres();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return new PostgresAgentStateStore(
                                ds, cfg.getSchema(), cfg.getTable(), cfg.isCreateIfNotExist());
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.mysql.state.MysqlAgentStateStore")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class MysqlStateStoreConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public StateStoreProvider mysqlStateStoreProvider() {
                return new StateStoreProvider() {
                    @Override
                    public AiConstants.StateStoreType type() {
                        return AiConstants.StateStoreType.MYSQL;
                    }

                    @Override
                    public AgentStateStore create() {
                        AiProperties.StateStore.Mysql cfg =
                                aiProperties.getStateStore().getMysql();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return new MysqlAgentStateStore(
                                ds, cfg.getDatabase(), cfg.getTable(), cfg.isCreateIfNotExist());
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.redis.state.RedisAgentStateStore")
        @ConditionalOnBean(RedissonClient.class)
        @RequiredArgsConstructor
        public static class RedisStateStoreConfiguration {

            private final AiProperties aiProperties;
            private final ObjectProvider<RedissonClient> redissonClientProvider;

            @Bean
            public StateStoreProvider redisStateStoreProvider() {
                return new StateStoreProvider() {
                    @Override
                    public AiConstants.StateStoreType type() {
                        return AiConstants.StateStoreType.REDIS;
                    }

                    @Override
                    public AgentStateStore create() {
                        RedissonClient client = redissonClientProvider.getIfAvailable();
                        if (client == null) {
                            throw new IllegalStateException("RedissonClient 未配置，无法构建 Redis 状态存储");
                        }
                        AiProperties.StateStore.Redis cfg =
                                aiProperties.getStateStore().getRedis();
                        return RedisAgentStateStore.builder()
                                .redissonClient(client)
                                .keyPrefix(cfg.getKeyPrefix())
                                .build();
                    }
                };
            }
        }

        // OssAgentStateStore 被 shade 进 agentscope.jar 恒在场，真正决定能否构建的是阿里云 OSS SDK
        @Configuration
        @ConditionalOnClass(name = {"io.agentscope.extensions.oss.OssAgentStateStore", "com.aliyun.oss.OSS"})
        @RequiredArgsConstructor
        public static class OssStateStoreConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public StateStoreProvider ossStateStoreProvider() {
                return new StateStoreProvider() {
                    @Override
                    public AiConstants.StateStoreType type() {
                        return AiConstants.StateStoreType.OSS;
                    }

                    @Override
                    public AgentStateStore create() {
                        AiProperties.StateStore.Oss cfg =
                                aiProperties.getStateStore().getOss();
                        OSS ossClient = new OSSClientBuilder()
                                .build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
                        return OssAgentStateStore.builder()
                                .ossClient(ossClient)
                                .bucketName(cfg.getBucketName())
                                .keyPrefix(cfg.getKeyPrefix())
                                .build();
                    }
                };
            }
        }

        // CosAgentStateStore 被 shade 进 agentscope.jar 恒在场，真正决定能否构建的是腾讯 COS SDK
        @Configuration
        @ConditionalOnClass(name = {"io.agentscope.extensions.cos.CosAgentStateStore", "com.qcloud.cos.COSClient"})
        @RequiredArgsConstructor
        public static class CosStateStoreConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public StateStoreProvider cosStateStoreProvider() {
                return new StateStoreProvider() {
                    @Override
                    public AiConstants.StateStoreType type() {
                        return AiConstants.StateStoreType.COS;
                    }

                    @Override
                    public AgentStateStore create() {
                        AiProperties.StateStore.Cos cfg =
                                aiProperties.getStateStore().getCos();
                        BasicCOSCredentials cred = new BasicCOSCredentials(cfg.getSecretId(), cfg.getSecretKey());
                        ClientConfig clientConfig = new ClientConfig(new Region(cfg.getRegion()));
                        COSClient cosClient = new COSClient(cred, clientConfig);
                        return CosAgentStateStore.builder()
                                .cosClient(cosClient)
                                .bucketName(cfg.getBucketName())
                                .keyPrefix(cfg.getKeyPrefix())
                                .build();
                    }
                };
            }
        }
    }

    /**
     * 渠道适配器装配：ChannelFactory Bean 名必须等于 {@code ai_channel_config.type}。
     * Spring 注入 {@code Map<String, ChannelFactory>} 时 key 为 Bean 名，ChannelBootstrap 直接按 type 查找。
     * webhook 型(飞书/企微)额外注册 CallbackController bean 让回调端点生效。
     */
    @Configuration
    public static class ChannelAdapterConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.channel.dingtalk.DingTalkChannel")
        public static class DingTalkFactoryConfiguration {

            @Bean(DingTalkChannel.TYPE)
            public ChannelFactory dingTalkChannelFactory() {
                return DingTalkChannel::fromProperties;
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.channel.feishu.FeishuChannel")
        public static class FeishuFactoryConfiguration {

            @Bean(FeishuChannel.TYPE)
            public ChannelFactory feishuChannelFactory() {
                return FeishuChannel::fromProperties;
            }

            // 飞书回调端点（webhook 入站）；channel 在 start() 时自注册到 FeishuChannelRegistry
            @Bean
            public FeishuCallbackController feishuCallbackController() {
                return new FeishuCallbackController();
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.channel.wecom.WeComChannel")
        public static class WeComFactoryConfiguration {

            @Bean(WeComChannel.TYPE)
            public ChannelFactory wecomChannelFactory() {
                return WeComChannel::fromProperties;
            }

            // 企微回调端点（webhook 入站）；channel 在 start() 时自注册到 WeComChannelRegistry
            @Bean
            public WeComCallbackController wecomCallbackController() {
                return new WeComCallbackController();
            }
        }
    }

    /**
     * 文档原文件存储后端（LOCAL/OSS）：知识库文档与对话附件共用。无条件注册（不依赖 {@code rag.enabled}），
     * rag 关闭时对话附件仍可存取原文件；OSS 客户端缺失由 {@link OssDocumentFileStorage} 内部 {@code ObjectProvider}
     * 判空降级报错，service 按 {@code List<DocumentFileStorage>} 的 {@code type()} 路由。
     */
    @Configuration
    public static class DocumentStorageConfiguration {

        @Bean
        public LocalDocumentFileStorage localDocumentFileStorage(AiProperties aiProperties) {
            return new LocalDocumentFileStorage(aiProperties);
        }

        @Bean
        public OssDocumentFileStorage ossDocumentFileStorage(
                ObjectProvider<OssClientManager> ossClientManagerProvider, AiProperties aiProperties) {
            return new OssDocumentFileStorage(ossClientManagerProvider, aiProperties);
        }
    }

    /**
     * 知识库（RAG）运行时装配：仅 {@code lambda.fusion.ai.rag.enabled=true} 时注册检索适配器与
     * 文档入库管线 Bean；未启用时 {@code AgentFactory} 判空跳过中间件挂载，管理 CRUD 不受影响。
     * 原文件存储后端已上移至 {@link DocumentStorageConfiguration} 无条件注册，此处不再重复。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "lambda.fusion.ai.rag", name = "enabled", havingValue = "true")
    public static class RagConfiguration {

        @Bean
        public SimpleKnowledgeAdapter simpleKnowledgeAdapter(
                KnowledgeBaseService knowledgeBaseService,
                EmbeddingModelResolver embeddingModelResolver,
                AiProperties aiProperties) {
            return new SimpleKnowledgeAdapter(knowledgeBaseService, embeddingModelResolver, aiProperties);
        }

        @Bean
        public DocumentIngestionService documentIngestionService(
                KnowledgeDocumentMapper knowledgeDocumentMapper,
                SimpleKnowledgeAdapter simpleKnowledgeAdapter,
                DocumentFileStorageResolver storageResolver,
                AiProperties aiProperties) {
            return new DocumentIngestionService(
                    knowledgeDocumentMapper, simpleKnowledgeAdapter, storageResolver, aiProperties);
        }
    }

    /**
     * 技能仓库源装配：每个后端一个 {@code @ConditionalOnClass} 嵌套配置，注册 {@link SkillRepositoryProvider}
     * bean（委托 AgentScope 已有仓库）。扩展未引入时不注册，{@code SkillRepositoryResolver} 对该 type 返回 null。
     */
    @Configuration
    public static class SkillRepositoryConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.core.skill.repository.mysql.MysqlSkillRepository")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class MysqlSkillRepositoryConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public SkillRepositoryProvider mysqlSkillRepositoryProvider() {
                return new SkillRepositoryProvider() {
                    @Override
                    public String type() {
                        return "MYSQL";
                    }

                    @Override
                    public AgentSkillRepository create() {
                        AiProperties.Skill.Repository.Mysql cfg =
                                aiProperties.getSkill().getRepository().getMysql();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return new MysqlSkillRepository(ds, cfg.isCreateIfNotExist(), cfg.isWriteable());
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository")
        @ConditionalOnBean(DataSource.class)
        @RequiredArgsConstructor
        public static class PostgresSkillRepositoryConfiguration {

            private final AiProperties aiProperties;
            private final DataSource dataSource;

            @Bean
            public SkillRepositoryProvider postgresSkillRepositoryProvider() {
                return new SkillRepositoryProvider() {
                    @Override
                    public String type() {
                        return "POSTGRES";
                    }

                    @Override
                    public AgentSkillRepository create() {
                        AiProperties.Skill.Repository.Postgres cfg =
                                aiProperties.getSkill().getRepository().getPostgres();
                        DataSource ds = StateStoreDataSources.resolveNamed(dataSource, cfg.getDatasource());
                        return new PostgresSkillRepository(ds, cfg.isCreateIfNotExist(), cfg.isWriteable());
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.core.skill.repository.GitSkillRepository")
        @RequiredArgsConstructor
        public static class GitSkillRepositoryConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SkillRepositoryProvider gitSkillRepositoryProvider() {
                return new SkillRepositoryProvider() {
                    @Override
                    public String type() {
                        return "GIT";
                    }

                    @Override
                    public AgentSkillRepository create() {
                        AiProperties.Skill.Repository.Git cfg =
                                aiProperties.getSkill().getRepository().getGit();
                        Path localPath =
                                StringUtils.isNotBlank(cfg.getLocalPath()) ? Path.of(cfg.getLocalPath()) : null;
                        return new GitSkillRepository(cfg.getRemoteUrl(), cfg.getBranch(), localPath, cfg.getSource());
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.core.nacos.skill.NacosSkillRepository")
        @RequiredArgsConstructor
        public static class NacosSkillRepositoryConfiguration {

            private final AiProperties aiProperties;

            @Bean
            public SkillRepositoryProvider nacosSkillRepositoryProvider() {
                return new SkillRepositoryProvider() {
                    @Override
                    public String type() {
                        return "NACOS";
                    }

                    @Override
                    public AgentSkillRepository create() {
                        AiProperties.Skill.Repository.Nacos cfg =
                                aiProperties.getSkill().getRepository().getNacos();
                        Properties props = new Properties();
                        props.setProperty(PropertyKeyConst.SERVER_ADDR, cfg.getServerAddr());
                        if (StringUtils.isNotBlank(cfg.getNamespaceId())) {
                            props.setProperty(PropertyKeyConst.NAMESPACE, cfg.getNamespaceId());
                        }
                        if (StringUtils.isNotBlank(cfg.getAccessKey())) {
                            props.setProperty(PropertyKeyConst.ACCESS_KEY, cfg.getAccessKey());
                        }
                        if (StringUtils.isNotBlank(cfg.getSecretKey())) {
                            props.setProperty(PropertyKeyConst.SECRET_KEY, cfg.getSecretKey());
                        }
                        AiService aiService;
                        try {
                            aiService = AiFactory.createAiService(props);
                        } catch (NacosException e) {
                            throw new IllegalStateException("Nacos AiService 创建失败: " + e.getMessage(), e);
                        }
                        return new NacosSkillRepository(aiService, cfg.getNamespaceId());
                    }
                };
            }
        }
    }

    /**
     * Dubbo 跨实例 Agent 缓存失效广播：每个实例暴露 {@link RemoteAgentCacheInvalidationService}，
     * 本地配置变更由 {@code DubboConfigInvalidationBroadcaster} 广播；远端收到后回放 {@code remote} 事件
     * 失效本地缓存。Dubbo 不在 classpath 或开关关闭时不装配，退化为单实例本地事件。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    @ConditionalOnProperty(prefix = "lambda.fusion.ai.cluster", name = "invalidation-broadcast", matchIfMissing = true)
    public static class DubboInvalidationConfiguration {

        @Bean
        @DubboService(interfaceClass = RemoteAgentCacheInvalidationService.class)
        public RemoteAgentCacheInvalidationService remoteAgentCacheInvalidationService(
                ApplicationEventPublisher publisher) {
            // 远端广播收到 -> 回放为 remote 事件，让 AgentFactory 等所有本地监听者失效；
            // remote 标记避免 DubboConfigInvalidationBroadcaster 二次外播形成回环
            return appId -> publisher.publishEvent(ConfigChangedEvent.remote(appId));
        }

        @Bean
        public DubboConfigInvalidationBroadcaster dubboConfigInvalidationBroadcaster() {
            // broadcast 集群：一次调用扩散到所有注册 provider（含自身，幂等无害）；
            // check=false 避免其他实例未就绪时阻塞本地启动
            ReferenceConfig<RemoteAgentCacheInvalidationService> reference = new ReferenceConfig<>();
            reference.setInterface(RemoteAgentCacheInvalidationService.class);
            reference.setCluster("broadcast");
            reference.setCheck(false);
            return new DubboConfigInvalidationBroadcaster(reference.get());
        }
    }

    /**
     * authority 远程用户查询引用：暴露 {@link RemoteUserService} Dubbo 引用，供 {@code CurrentUserQueryTool}
     * 获取用户身份详情（昵称/组织/角色/账户状态）。Dubbo 不在 classpath 时不装配，工具退化为仅返回对话上下文中的基础身份。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    public static class DubboRemoteUserConfiguration {

        @Bean
        public RemoteUserService remoteUserService() {
            // check=false：authority 未就绪/缺失时不阻塞本地启动，调用期失败由工具降级处理
            ReferenceConfig<RemoteUserService> reference = new ReferenceConfig<>();
            reference.setInterface(RemoteUserService.class);
            reference.setCheck(false);
            return reference.get();
        }
    }

    /**
     * 定时 Agent 任务调度装配：仅在 {@code lambda.fusion.ai.schedule.enabled=true} 且 Quartz 扩展
     * 在 classpath 时注册。自建 Quartz 内存调度器（RAMJobStore），业务任务定义以
     * {@code ai_sub_agent}(category=SCHEDULED_TASK) 为唯一事实来源，启动时经 {@code AgentTaskBootstrap}
     * 重注册恢复。Bean 由 {@code AiConfigure} 组件扫描发现，此处仅注册调度器本身。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler")
    public static class ScheduleConfiguration {

        @Bean(destroyMethod = "")
        public QuartzAgentScheduler quartzAgentScheduler(AiProperties aiProperties) {
            // 自建内存 Quartz 调度器；destroyMethod="" 交由 DisposableBean 之外的容器生命周期不管，
            // 避免误关。生产 JDBC 持久化/集群为 §8 开放点，本期单机内存即可。
            return QuartzAgentScheduler.builder()
                    .schedulerId(aiProperties.getSchedule().getSchedulerId())
                    .autoStart(true)
                    .build();
        }
    }
}
