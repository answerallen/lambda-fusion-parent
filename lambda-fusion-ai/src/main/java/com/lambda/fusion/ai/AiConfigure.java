package com.lambda.fusion.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.channel.ChannelFactoryProvider;
import com.lambda.fusion.ai.channel.service.ChannelConfigService;
import com.lambda.fusion.ai.runtime.gateway.ChannelBootstrap;
import com.lambda.fusion.ai.runtime.gateway.ChannelConfigApplier;
import com.lambda.fusion.ai.runtime.gateway.ChannelLifecycle;
import com.lambda.fusion.ai.runtime.sandbox.SandboxBackendProvider;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.state.StateStoreDataSources;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import com.lambda.fusion.ai.skill.SkillRepositoryProvider;
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
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.oss.OssAgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec;
import io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
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
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
                List<ChannelFactoryProvider> factories) {
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

    /**
     * 分布式状态存储后端装配：每个后端一个 {@code @ConditionalOnClass} 嵌套配置，注册
     * {@link StateStoreProvider} bean。扩展未在 classpath（或 Redis 客户端缺失）时不注册，
     * {@code AiAgentFactory#resolveStateStore} 找不到匹配 provider 时回退 MEMORY。
     */
    @Configuration
    public static class StateStoreConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.postgresql.state.PostgresAgentStateStore")
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

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.oss.OssAgentStateStore")
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

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.cos.CosAgentStateStore")
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
     * 渠道适配器装配：每个平台一个 {@code @ConditionalOnClass} 嵌套配置，注册 {@link ChannelFactoryProvider}
     * bean（委托 {@code XxxChannel.fromProperties}）。webhook 型(飞书/企微)额外注册 CallbackController bean
     * 让回调端点生效。扩展未在 classpath 时不注册，{@code ChannelBootstrap} 对该 type 跳过。
     */
    @Configuration
    public static class ChannelAdapterConfig {

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.channel.dingtalk.DingTalkChannel")
        public static class DingTalkFactoryConfiguration {

            @Bean
            public ChannelFactoryProvider dingTalkChannelFactory() {
                return new ChannelFactoryProvider() {
                    @Override
                    public String type() {
                        return DingTalkChannel.TYPE;
                    }

                    @Override
                    public Channel create(String channelId, ChannelConfig routing, Map<String, Object> properties) {
                        return DingTalkChannel.fromProperties(channelId, routing, properties);
                    }
                };
            }
        }

        @Configuration
        @ConditionalOnClass(name = "io.agentscope.extensions.channel.feishu.FeishuChannel")
        public static class FeishuFactoryConfiguration {

            @Bean
            public ChannelFactoryProvider feishuChannelFactory() {
                return new ChannelFactoryProvider() {
                    @Override
                    public String type() {
                        return FeishuChannel.TYPE;
                    }

                    @Override
                    public Channel create(String channelId, ChannelConfig routing, Map<String, Object> properties) {
                        return FeishuChannel.fromProperties(channelId, routing, properties);
                    }
                };
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

            @Bean
            public ChannelFactoryProvider wecomChannelFactory() {
                return new ChannelFactoryProvider() {
                    @Override
                    public String type() {
                        return WeComChannel.TYPE;
                    }

                    @Override
                    public Channel create(String channelId, ChannelConfig routing, Map<String, Object> properties) {
                        return WeComChannel.fromProperties(channelId, routing, properties);
                    }
                };
            }

            // 企微回调端点（webhook 入站）；channel 在 start() 时自注册到 WeComChannelRegistry
            @Bean
            public WeComCallbackController wecomCallbackController() {
                return new WeComCallbackController();
            }
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
}
