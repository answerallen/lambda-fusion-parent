package com.lambda.fusion.ai.rag.storage;

import com.lambda.cloud.oss.client.OssClient;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.OutputStream;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;

/**
 * OSS 文档原文件存储：走 lambda-cloud-starter-oss 的 {@link OssClientManager}（多客户端模型，
 * 与 lambda-fusion-oss 用法一致），objectKey = {@code {keyPrefix}{tenantId}/{kbId}/{documentId}.{ext}}。
 * starter 未注册客户端（{@code lambda.oss.clients} 为空）时，存储操作抛带明确提示的业务异常，
 * 需配置 OSS 客户端或改用 LOCAL。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class OssDocumentFileStorage implements DocumentFileStorage {

    public static final String TYPE = "OSS";

    private final ObjectProvider<OssClientManager> ossClientManagerProvider;
    private final AiProperties aiProperties;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String store(Path source, String relativeName) {
        String objectKey = keyPrefix() + relativeName;
        client().upload(source.toFile(), objectKey);
        return objectKey;
    }

    @Override
    public void delete(String storagePath) {
        client().delete(storagePath);
    }

    @Override
    public void download(String storagePath, OutputStream out) {
        client().outStream(storagePath, out);
    }

    private String keyPrefix() {
        return aiProperties.getRag().getDocumentStorage().getOss().getKeyPrefix();
    }

    // 客户端名为空走默认客户端；manager 缺失/为空说明 OSS 未配置
    private OssClient client() {
        OssClientManager manager = ossClientManagerProvider.getIfAvailable();
        if (manager == null || manager.isEmpty()) {
            throw new AiBusinessException(
                    AiErrorCode.CONFIGURATION_ERROR, "OSS 客户端未配置(lambda.oss.clients)，知识库文件存储请配置 OSS 客户端或改用 LOCAL");
        }
        String clientName = aiProperties.getRag().getDocumentStorage().getOss().getClientName();
        return StringUtils.isBlank(clientName) ? manager.getDefault() : manager.get(clientName);
    }
}
