package com.lambda.fusion.ai;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.runtime.sandbox.SandboxBackendProvider;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec;
import io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
}
