package com.lambda.fusion.ai.rag.runtime;

import com.lambda.fusion.ai.AiConstants.DocumentChunkStrategy;
import com.lambda.fusion.ai.AiProperties;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextChunker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * 通用文档切割器：在文件解析与向量入库之间提供稳定的切割策略和章节元数据。
 * AUTO 做可证伪的结构判断（短文本整篇保留，识别到标题按章节切割，否则回退段落）；
 * HEADING 下章节过长继续按段落切割，防止标题策略产生超长向量文本。
 *
 * @author Jin
 */
public final class DocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s*(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern CHINESE_HEADING =
            Pattern.compile("^\\s*第([0-9一二三四五六七八九十百千万零〇两]+)(章|节|篇|部分|条)\\s*[：:、.\\-]?\\s*(.*?)\\s*$");
    private static final Pattern NUMBERED_DOTTED_HEADING =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+){1,5})[.、)]?\\s+(.{1,120}?)\\s*$");
    private static final Pattern NUMBERED_ORDINAL_HEADING = Pattern.compile("^\\s*(\\d+)[.、)]\\s*(.{1,120}?)\\s*$");
    private static final Pattern ENGLISH_HEADING = Pattern.compile(
            "^\\s*(chapter|section|part)\\s+([a-z0-9ivxlc]+(?:\\.[a-z0-9ivxlc]+)*)(?:\\s*[：:.-]?\\s*(.*?))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private DocumentChunker() {}

    /**
     * 按指定策略切割文本，生成确定性的文档内 chunkId。
     *
     * @param documentId 来源文档ID
     * @param text 已抽取的完整文本
     * @param strategy 文档持久化的切割策略
     * @param options 模块级切割参数
     * @return 非空白切块；空文档返回空列表
     */
    public static List<IngestChunk> chunk(
            String documentId, String text, DocumentChunkStrategy strategy, AiProperties.Rag.Chunking options) {
        if (StringUtils.isBlank(documentId)) {
            throw new IllegalArgumentException("Document ID cannot be blank");
        }
        validateOptions(options);
        String normalized = normalize(text);
        if (StringUtils.isBlank(normalized)) {
            return List.of();
        }

        DocumentChunkStrategy resolved = resolveStrategy(normalized, strategy, options);
        List<DraftChunk> drafts =
                switch (resolved) {
                    case WHOLE -> List.of(new DraftChunk(normalized, null));
                    case HEADING -> splitByHeading(normalized, options);
                    case PARAGRAPH -> splitPlain(normalized, options, SplitStrategy.PARAGRAPH);
                    case TOKEN -> splitPlain(normalized, options, SplitStrategy.TOKEN);
                    case AUTO -> throw new IllegalStateException("AUTO strategy must be resolved before chunking");
                };

        List<IngestChunk> chunks = new ArrayList<>(drafts.size());
        for (DraftChunk draft : drafts) {
            if (StringUtils.isBlank(draft.text())) {
                continue;
            }
            int index = chunks.size();
            chunks.add(new IngestChunk(documentId + "-" + index, draft.text().trim(), index, draft.sectionPath()));
        }
        return chunks;
    }

    static DocumentChunkStrategy resolveStrategy(
            String text, DocumentChunkStrategy strategy, AiProperties.Rag.Chunking options) {
        DocumentChunkStrategy requested = strategy != null ? strategy : DocumentChunkStrategy.AUTO;
        if (requested != DocumentChunkStrategy.AUTO) {
            return requested;
        }
        if (text.length() <= options.getWholeDocumentMaxChars()) {
            return DocumentChunkStrategy.WHOLE;
        }
        return countHeadings(text) >= 2 ? DocumentChunkStrategy.HEADING : DocumentChunkStrategy.PARAGRAPH;
    }

    private static List<DraftChunk> splitPlain(
            String text, AiProperties.Rag.Chunking options, SplitStrategy splitStrategy) {
        List<String> texts =
                TextChunker.chunkText(text, options.getChunkSize(), splitStrategy, options.getOverlapSize());
        return texts.stream().map(chunk -> new DraftChunk(chunk, null)).toList();
    }

    private static List<DraftChunk> splitByHeading(String text, AiProperties.Rag.Chunking options) {
        List<Section> sections = parseSections(text);
        if (sections.stream().noneMatch(section -> section.sectionPath() != null)) {
            return splitPlain(text, options, SplitStrategy.PARAGRAPH);
        }

        List<DraftChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            String body = section.body().trim();
            if (body.isEmpty()) {
                continue;
            }
            String prefix = embeddingPrefix(section.sectionPath(), options.getChunkSize());
            int bodyChunkSize = Math.max(1, options.getChunkSize() - prefix.length());
            int bodyOverlap = Math.min(options.getOverlapSize(), Math.max(0, bodyChunkSize - 1));
            List<String> parts = TextChunker.chunkText(body, bodyChunkSize, SplitStrategy.PARAGRAPH, bodyOverlap);
            for (String part : parts) {
                if (StringUtils.isNotBlank(part)) {
                    chunks.add(new DraftChunk(prefix + part.trim(), section.sectionPath()));
                }
            }
        }
        return chunks;
    }

    private static List<Section> parseSections(String text) {
        List<Section> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        String currentPath = null;
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\\n", -1)) {
            Heading heading = parseHeading(line);
            if (heading == null) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(line);
                continue;
            }

            addSection(sections, currentPath, body);
            updateHeadingStack(headingStack, heading);
            currentPath = headingStack.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" / "));
            body = new StringBuilder();
        }
        addSection(sections, currentPath, body);
        return sections;
    }

    private static void addSection(List<Section> sections, String sectionPath, StringBuilder body) {
        if (StringUtils.isNotBlank(body)) {
            sections.add(new Section(sectionPath, body.toString()));
        }
    }

    private static void updateHeadingStack(List<String> stack, Heading heading) {
        int targetSize = Math.max(1, heading.level());
        while (stack.size() > targetSize) {
            stack.remove(stack.size() - 1);
        }
        while (stack.size() < targetSize) {
            stack.add(null);
        }
        stack.set(targetSize - 1, heading.title());
    }

    private static int countHeadings(String text) {
        int count = 0;
        for (String line : text.split("\\n")) {
            if (parseHeading(line) != null) {
                count++;
            }
        }
        return count;
    }

    private static Heading parseHeading(String line) {
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new Heading(markdown.group(1).length(), markdown.group(2).trim());
        }

        Matcher chinese = CHINESE_HEADING.matcher(line);
        if (chinese.matches()) {
            String unit = chinese.group(2);
            int level =
                    switch (unit) {
                        case "节" -> 2;
                        case "条" -> 3;
                        default -> 1;
                    };
            String tail = StringUtils.trimToEmpty(chinese.group(3));
            String title = "第" + chinese.group(1) + unit + (tail.isEmpty() ? "" : " " + tail);
            return new Heading(level, title);
        }

        Matcher english = ENGLISH_HEADING.matcher(line);
        if (english.matches()) {
            String kind = english.group(1).toLowerCase(Locale.ROOT);
            int level = "section".equals(kind) ? 2 : 1;
            return new Heading(level, line.trim());
        }

        Matcher numbered = NUMBERED_DOTTED_HEADING.matcher(line);
        if (!numbered.matches()) {
            numbered = NUMBERED_ORDINAL_HEADING.matcher(line);
        }
        if (numbered.matches()) {
            int level = Math.min(6, numbered.group(1).split("\\.").length);
            return new Heading(
                    level, numbered.group(1) + " " + numbered.group(2).trim());
        }
        return null;
    }

    private static String embeddingPrefix(String sectionPath, int chunkSize) {
        if (StringUtils.isBlank(sectionPath) || chunkSize < 4) {
            return "";
        }
        int maxPathLength = Math.max(1, chunkSize / 3);
        String displayPath = maxPathLength < 4
                ? StringUtils.left(sectionPath, maxPathLength)
                : StringUtils.abbreviate(sectionPath, maxPathLength);
        return displayPath + "\n";
    }

    private static String normalize(String text) {
        return StringUtils.defaultString(text)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static void validateOptions(AiProperties.Rag.Chunking options) {
        if (options == null) {
            throw new IllegalArgumentException("Chunking options cannot be null");
        }
        if (options.getChunkSize() <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (options.getOverlapSize() < 0 || options.getOverlapSize() >= options.getChunkSize()) {
            throw new IllegalArgumentException("Overlap size must be non-negative and less than chunk size");
        }
        if (options.getWholeDocumentMaxChars() <= 0) {
            throw new IllegalArgumentException("Whole document max chars must be positive");
        }
    }

    private record Heading(int level, String title) {}

    private record Section(String sectionPath, String body) {}

    private record DraftChunk(String text, String sectionPath) {}
}
