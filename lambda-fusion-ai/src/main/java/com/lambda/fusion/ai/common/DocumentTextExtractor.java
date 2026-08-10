package com.lambda.fusion.ai.common;

import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.TikaReader;
import io.agentscope.core.rag.reader.WordReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;

/**
 * 文档文本抽取共享工具：按扩展名选择 AgentScope {@link Reader} 解析文件并抽取全文。
 *
 * <p>抽取逻辑原属 {@code DocumentIngestionService}（知识库入库管线），此处上移为无状态静态工具，
 * 供知识库入库与对话附件（文档附件注入 prompt）两处复用，避免把 rag 的条件装配/入库语义
 * 拖进对话链路。本类不依赖任何 Spring Bean，{@code rag.enabled=false} 时对话附件仍可用。
 *
 * <p>临时文件生命周期归调用方负责（下载到临时文件 → 调 {@link #extractText} → finally 删除）。
 *
 * @author Jin
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {}

    /**
     * 抽取文件全文并拼接（多文档以换行分隔）。截断由调用方按需控制。
     *
     * @param fileType 扩展名（pdf/doc/docx/txt/md 等，决定 Reader 选型）
     * @param file 已落盘的文件（二进制走 fromPath，文本走 fromString）
     * @return 抽取文本；无内容时返回空串
     * @throws IOException 文本读取编码失败时抛出
     */
    public static String extractText(String fileType, Path file) throws IOException {
        Reader reader = resolveReader(fileType);
        ReaderInput input = buildReaderInput(fileType, file);
        var documents = reader.read(input).block();
        if (documents == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var doc : documents) {
            String contentText = doc.getMetadata().getContentText();
            if (StringUtils.isNotBlank(contentText)) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(contentText);
            }
        }
        return text.toString();
    }

    // 按扩展名选 Reader；TikaReader 兜底（调用方已白名单校验，正常不会走到）
    public static Reader resolveReader(String fileType) {
        return switch (StringUtils.defaultString(fileType)) {
            case "pdf" -> new PDFReader();
            case "doc", "docx" -> new WordReader();
            case "txt", "md" -> new TextReader();
            default -> new TikaReader();
        };
    }

    // ReaderInput 构造按 reader 类型区分：二进制走 fromPath（reader 用 PDFBox/POI 自行解析），
    // 文本走 fromString（TextReader 期望内容）；文本容错非 UTF-8 编码
    public static ReaderInput buildReaderInput(String fileType, Path file) throws IOException {
        if ("txt".equalsIgnoreCase(fileType) || "md".equalsIgnoreCase(fileType)) {
            return ReaderInput.fromString(readTextWithEncodingFallback(file));
        }
        return ReaderInput.fromPath(file);
    }

    // 优先 UTF-8 读取；非 UTF-8（中文 Windows 常见 GBK）回退 GBK，避免 MalformedInputException
    static String readTextWithEncodingFallback(Path file) throws IOException {
        try {
            return Files.readString(file);
        } catch (MalformedInputException e) {
            return new String(Files.readAllBytes(file), Charset.forName("GBK"));
        }
    }
}
