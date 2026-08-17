# RAG 文档切割与来源契约

## 1. 数据流

知识库文档统一经过以下管线：

```text
原文件存储 → 抽取完整文本 → 解析文档切割策略 → 生成带来源元数据的 chunk → 向量入库
```

文件 Reader 只负责 PDF、Word、文本等格式解析，不再决定业务切割语义。切割由
`DocumentChunker` 统一处理，避免不同调用方分别解释 chunk。

## 2. 文档切割策略

每个 `ai_knowledge_document` 通过 `chunk_strategy` 持久化策略，重新入库默认沿用原策略。

| 策略 | 语义 |
|---|---|
| `AUTO` | 短文档整篇保留；检测到至少两个标题时按章节；否则按段落 |
| `WHOLE` | 整篇作为一个 chunk，调用方应确保文档长度适合嵌入模型 |
| `HEADING` | 识别 Markdown、中文章节、英文章节和数字层级标题；超长章节继续按段落切割 |
| `PARAGRAPH` | 按段落边界和目标长度切割 |
| `TOKEN` | 按 AgentScope 的近似 token 规则切割 |

`HEADING` 当前依据抽取文本中的标题语法识别章节，不读取 PDF 字号或 Word Heading 样式；只有视觉样式、
没有标题编号或关键字的文档会回退段落切割，后续需要在格式解析层补充版式元数据。

模块级参数：

```yaml
lambda:
  fusion:
    ai:
      rag:
        chunking:
          chunk-size: 512
          overlap-size: 50
          whole-document-max-chars: 2000
```

`TOKEN` 策略下 `chunk-size` 与 `overlap-size` 表示近似 token 数；其他策略下表示字符数。

上传接口的 multipart 表单可传 `chunkStrategy`，默认 `AUTO`。重新入库接口可选传入新策略，
不传则沿用文档行中的策略。

## 3. Chunk 来源元数据

每个新入库 chunk 的向量 payload 必须包含：

- `kbId`
- `tenantId`
- `fileName`
- `chunkIndex`（零基）
- `chunkCount`
- `sectionPath`（存在标题结构时）

检索返回必须继续携带 `docId` 与向量元数据中的 `chunkId`。自动 RAG 注入与 Agentic
检索工具复用同一来源格式，向模型明确展示来源文件、文档 ID、章节、分块位置和分数。

旧 PGVector 数据已包含 `fileName`，升级后无需重新入库即可显示文件来源；`chunkCount` 和
`sectionPath` 只有重新入库后才会补齐。

## 4. 当前检索边界

- 全局默认最终检索条数为 5，表示 chunk 数而不是文档数。
- 默认分数阈值为 0.5，是余弦相似度下限，不是正确概率。
- 自动注入字符预算默认 4000；单个超长 chunk 会保留来源头并截断正文。
- 当前候选召回数量与最终返回数量仍共用 `limit`。

按文档限制 chunk 数必须与扩大候选召回配套实施；只在当前 Top-K 上去重会造成结果不足，
因此候选召回、按文档分组、混合检索和 rerank 应作为同一个后续闭环实现。
