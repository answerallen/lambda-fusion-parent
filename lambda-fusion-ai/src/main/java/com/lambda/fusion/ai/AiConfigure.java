package com.lambda.fusion.ai;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.runtime.gateway.ChannelLifecycle;
import com.lambda.fusion.ai.runtime.sandbox.SandboxBackendProvider;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.state.StateStoreDataSources;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import io.agentscope.core.state.AgentStateStore;
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
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                ChannelManager channelManager, HarnessGateway gateway, List<Channel> channels) {
            return new ChannelLifecycle(channelManager, gateway, channels);
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
}
