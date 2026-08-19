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
 * 本地目录文档原文件存储（默认）：{@code {root}/{tenantId}/{kbId}/{documentId}.{ext}}。
 * root 解析与 FILE state store 的目录习惯一致：显式配置
 * {@code lambda.fusion.ai.rag.document-storage.local.root} 优先，其次
 * {@code workspace.root/knowledge-files}，最后 {@code ~/.agentscope/fusion/knowledge-files}。
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

    // root 解析：显式配置 > workspace.root/knowledge-files > ~/.agentscope/fusion/knowledge-files
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
