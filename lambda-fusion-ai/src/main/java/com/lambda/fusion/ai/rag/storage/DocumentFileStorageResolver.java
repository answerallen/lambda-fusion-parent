package com.lambda.fusion.ai.rag.storage;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 文档原文件存储解析器：按 {@code storageType} 在已注册的 {@link DocumentFileStorage} 中路由匹配实现。
 *
 * <p>对齐 {@code SkillRepositoryResolver} 模式。storage bean 在 {@code RagConfiguration}
 * （{@code rag.enabled=true}）下注册；rag 关闭时本 bean 仍存在但候选列表为空，
 * {@link #resolve(String)} 将抛 {@link AiErrorCode#DOCUMENT_STORAGE_NOT_SUPPORTED}，
 * 与改造前各调用方内联路由的行为一致。
 *
 * <p>统一路由点消除了 upload / download / delete / 异步入库 四处重复的 stream-filter 逻辑，
 * 并确保「按 document 行记录的 storageType 路由」（而非当前配置）这一语义在全链路一致--
 * 存储配置变更后，旧文档的下载/删除/入库仍能命中其原始存储后端。
 *
 * @author Jin
 */
@Component
public class DocumentFileStorageResolver {

    private final List<DocumentFileStorage> storages;

    public DocumentFileStorageResolver(List<DocumentFileStorage> storages) {
        this.storages = storages;
    }

    /**
     * 按 type 路由存储后端。
     *
     * @param type storageType（LOCAL / OSS），来自 document 行或当前配置
     * @return 匹配的存储实现
     * @throws AiBusinessException 无匹配类型时抛 {@link AiErrorCode#DOCUMENT_STORAGE_NOT_SUPPORTED}
     */
    public DocumentFileStorage resolve(String type) {
        return storages.stream()
                .filter(storage -> storage.type().equalsIgnoreCase(StringUtils.defaultString(type)))
                .findFirst()
                .orElseThrow(() -> new AiBusinessException(AiErrorCode.DOCUMENT_STORAGE_NOT_SUPPORTED, type));
    }
}
