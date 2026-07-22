package com.lambda.fusion.ai.rag.storage;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * 知识库文档原文件存储 SPI（扩展点）。
 *
 * <p>内置 LOCAL（本地目录，默认）/ OSS（lambda-cloud-starter-oss）两个实现，
 * 由 {@code AiConfigure.RagConfiguration} 装配；自定义后端实现本接口注册为 Bean
 * 即可（按 {@link #type()} 路由，对齐 {@code StateStoreProvider} 模式）。
 *
 * @author Jin
 */
public interface DocumentFileStorage {

    /** 存储类型标识：LOCAL / OSS。 */
    String type();

    /**
     * 持久化原文件，返回存储路径（本地相对路径或 OSS objectKey）。
     *
     * @param source 源文件（上传临时文件，调用方负责后续清理）
     * @param relativeName 相对名（{@code {tenantId}/{kbId}/{documentId}.{ext}}，由调用方生成）
     * @return 存储路径
     */
    String store(Path source, String relativeName);

    /** 删除原文件（不存在不报错）。 */
    void delete(String storagePath);

    void download(String storagePath, OutputStream out);
}
