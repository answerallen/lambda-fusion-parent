package com.lambda.fusion.ai.rag.storage;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 将文档原文件保存在本地目录，路径格式为 {@code {root}/{tenantId}/{kbId}/{documentId}.{ext}}。
 * 根目录依次取文档存储的显式配置、{@code workspace.root/knowledge-files} 和用户目录下的默认路径。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class LocalDocumentFileStorage implements DocumentFileStorage {

    public static final String TYPE = "LOCAL";

    private final AiProperties aiProperties;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String store(Path source, String relativeName) {
        Path target = resolveRoot().resolve(relativeName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return relativeName;
        } catch (IOException e) {
            throw new AiBusinessException(AiErrorCode.DOCUMENT_STORAGE_ERROR, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolveRoot().resolve(storagePath));
        } catch (IOException e) {
            throw new AiBusinessException(AiErrorCode.DOCUMENT_STORAGE_ERROR, e);
        }
    }

    @Override
    public void download(String storagePath, OutputStream out) {
        try {
            Files.copy(resolveRoot().resolve(storagePath), out);
        } catch (IOException e) {
            throw new AiBusinessException(AiErrorCode.DOCUMENT_STORAGE_ERROR, e);
        }
    }

    private Path resolveRoot() {
        String configured =
                aiProperties.getRag().getDocumentStorage().getLocal().getRoot();
        if (StringUtils.isNotBlank(configured)) {
            return Path.of(configured);
        }
        String workspaceRoot = aiProperties.getWorkspace().getRoot();
        if (StringUtils.isNotBlank(workspaceRoot)) {
            return Path.of(workspaceRoot, "knowledge-files");
        }
        return Paths.get(System.getProperty("user.home"), ".agentscope", "fusion", "knowledge-files");
    }
}
