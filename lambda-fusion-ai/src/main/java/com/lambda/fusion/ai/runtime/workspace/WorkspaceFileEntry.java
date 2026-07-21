package com.lambda.fusion.ai.runtime.workspace;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Workspace 文件条目。
 *
 * @author Jin
 */
@Schema(description = "Workspace 文件条目")
public record WorkspaceFileEntry(String name, String path, boolean directory, long size, long updatedAt) {}
