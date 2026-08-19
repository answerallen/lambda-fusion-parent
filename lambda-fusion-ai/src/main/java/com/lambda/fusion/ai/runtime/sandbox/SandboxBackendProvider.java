package com.lambda.fusion.ai.runtime.sandbox;

import com.lambda.fusion.ai.AiConstants.SandboxBackend;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import java.nio.file.Path;

/**
 * 沙箱后端提供者：为指定后端构建 {@link SandboxFilesystemSpec}。每个后端一个实现，
 * 按 {@code @ConditionalOnClass} 条件装配（扩展不在 classpath 时不注册）。
 *
 * @author Jin
 */
public interface SandboxBackendProvider {

    SandboxBackend backend();

    SandboxFilesystemSpec create(AppEntity app, Path hostWorkspace);
}
