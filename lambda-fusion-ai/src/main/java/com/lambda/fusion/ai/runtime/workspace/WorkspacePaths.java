package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Workspace 路径解析。每个 WORKSPACE 型应用对应一个目录：
 * {@code {workspace.root}/tenants/{tenantId}/apps/{appId}/}。
 *
 * @author Jin
 */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspacePaths {

    private static final String DEFAULT_ROOT = System.getProperty("user.home") + "/.agentscope/fusion";

    private final AiProperties aiProperties;

    public Path resolveAppWorkspace(String tenantId, String appId) {
        String root = StringUtils.defaultIfBlank(aiProperties.getWorkspace().getRoot(), DEFAULT_ROOT);
        return Paths.get(root, "tenants", Objects.toString(tenantId, "_default"), "apps", appId);
    }
}
