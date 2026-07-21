package com.lambda.fusion.ai.runtime.sandbox;

import com.lambda.fusion.ai.AiConstants.SandboxBackend;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 沙箱 spec 解析器：按应用 {@code sandboxBackend} 找到匹配的 {@link SandboxBackendProvider} 构建 spec。
 *
 * <p>HOST 或对应后端的扩展未安装时返回 {@link Optional#empty()}，调用方回退到宿主文件系统。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SandboxSpecResolver {

    private final List<SandboxBackendProvider> providers;

    public Optional<SandboxFilesystemSpec> resolve(AppEntity app, Path hostWorkspace) {
        SandboxBackend backend =
                SandboxBackend.of(StringUtils.defaultIfBlank(app.getSandboxBackend(), SandboxBackend.HOST.getCode()));
        if (backend == null || backend == SandboxBackend.HOST) {
            return Optional.empty();
        }
        Optional<SandboxFilesystemSpec> spec = providers.stream()
                .filter(p -> p.backend() == backend)
                .findFirst()
                .map(p -> p.create(app, hostWorkspace));
        if (spec.isEmpty()) {
            log.warn("沙箱后端 {} 的扩展未安装，将回退到宿主文件系统", backend);
        }
        return spec;
    }

    /**
     * 解析隔离粒度，配置非法时回退 AGENT。
     */
    public static IsolationScope parseIsolationScope(AiProperties aiProperties) {
        try {
            return IsolationScope.valueOf(
                    StringUtils.defaultIfBlank(aiProperties.getSandbox().getIsolationScope(), "AGENT")
                            .toUpperCase());
        } catch (Exception e) {
            return IsolationScope.AGENT;
        }
    }
}
