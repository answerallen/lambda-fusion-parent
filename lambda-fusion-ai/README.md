# Lambda Fusion AI 模块

Lambda Fusion AI 是一个企业级AI应用平台模块，基于LangChain4j框架实现RAG（检索增强生成）知识库问答系统。该模块提供完整的AI能力集成，支持多租户架构，为企业提供智能问答、文档理解和知识管理解决方案。

## 核心特性

### 🎯 RAG知识库系统
- **智能检索**: 基于向量相似度和关键词的混合检索
- **文档理解**: 支持PDF、Word、Excel等多种格式文档解析
- **知识管理**: 多租户知识库隔离，支持分类管理
- **实时问答**: 支持流式和非流式对话模式

### 🚀 已实现功能
- ✅ **知识库管理**: 创建、配置、更新、删除知识库
- ✅ **文档管理**: 文档上传、异步处理、状态跟踪
- ✅ **LLM模型管理**: 多厂商模型注册和配置（OpenAI、Ollama）
- ✅ **对话会话管理**: 会话创建、归档、历史管理
- ✅ **消息管理**: 同步/异步消息发送、用户反馈
- ✅ **提示词模板**: 模板创建、变量渲染、系统模板
- ✅ **向量检索**: pgvector集成，支持相似度搜索
- ✅ **文档处理**: 自动分段、向量化、批量处理

### 🔄 开发中功能
- ⏳ **高级RAG管道**: 可视化管道编排和配置
- ⏳ **多模态支持**: 图像、音频文档处理
- ⏳ **智能分析**: 对话质量分析、知识覆盖度统计
- ⏳ **API集成**: 更多LLM厂商支持（Claude、文心一言等）

## 技术架构

### 核心技术栈
- **AI框架**: LangChain4j 1.10.0-beta18
- **应用框架**: Spring Boot 3.x
- **数据访问**: MyBatis Plus
- **向量数据库**: PostgreSQL + pgvector 0.1.6
- **缓存**: Redis + Caffeine
- **文档解析**: Apache PDFBox + Apache POI
- **配置管理**: Nacos集成
- **数据库版本**: Liquibase

### 架构设计
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │   Service       │    │   Data Access   │
│   Controllers   │───▶│   Business      │───▶│   MyBatis +     │
│   (6个控制器)    │    │   Logic         │    │   VectorRepo    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   LangChain4j   │    │   Document      │    │   PostgreSQL    │
│   Integration   │    │   Processor     │    │   + pgvector    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## API接口文档

### 知识库管理 `/v1/knowledge-bases`

| 方法 | 路径 | 描述 | 参数 |
|------|------|------|------|
| POST | `/` | 创建知识库 | CreateKnowledgeBase |
| GET | `/page` | 分页查询 | pageNum, pageSize, tenantId, status |
| GET | `/{id}` | 查询详情 | id |
| PUT | `/{id}` | 更新配置 | id, UpdateKnowledgeBase |
| DELETE | `/{id}` | 删除知识库 | id |
| GET | `/list` | 列表查询 | tenantId, status |

### 文档管理 `/v1/knowledge-bases/{kbId}/documents`

| 方法 | 路径 | 描述 | 参数 |
|------|------|------|------|
| POST | `/` | 上传文档 | kbId, file, uploadedBy |
| GET | `/page` | 分页查询 | kbId, pageNum, pageSize, status |
| GET | `/` | 列表查询 | kbId, status |
| GET | `/{docId}` | 查询详情 | kbId, docId |
| DELETE | `/{docId}` | 删除文档 | kbId, docId |
| GET | `/{docId}/status` | 处理状态 | kbId, docId |
| GET | `/{docId}/chunks` | 文档分段 | kbId, docId, pageNum, pageSize |
| POST | `/{docId}/reprocess` | 重新处理 | kbId, docId |

### 对话管理 `/v1/chat`

| 方法 | 路径 | 描述 | 参数 |
|------|------|------|------|
| POST | `/sessions` | 创建会话 | CreateSession |
| GET | `/sessions` | 会话列表 | userId, status |
| POST | `/sessions/{sessionId}/messages` | 发送消息 | sessionId, SendMessage |
| POST | `/sessions/{sessionId}/messages/stream` | 流式消息 | sessionId, SendMessage |
| GET | `/sessions/{sessionId}/messages` | 消息历史 | sessionId, limit |
| POST | `/sessions/{sessionId}/messages/{messageId}/feedback` | 用户反馈 | sessionId, messageId, feedback |

### LLM模型管理 `/v1/llm-models`

| 方法 | 路径 | 描述 | 参数 |
|------|------|------|------|
| POST | `/` | 注册模型 | RegisterModel |
| GET | `/` | 模型列表 | provider, status |
| PUT | `/{id}` | 更新配置 | id, UpdateModel |
| DELETE | `/{id}` | 删除模型 | id |

### 提示词模板 `/v1/prompt-templates`

| 方法 | 路径 | 描述 | 参数 |
|------|------|------|------|
| POST | `/` | 创建模板 | CreateTemplate |
| GET | `/` | 模板列表 | category, type |
| POST | `/{id}/render` | 渲染模板 | id, variables |
| GET | `/system` | 系统模板 | category |

## 数据模型

### 核心实体关系
```
KnowledgeBase (知识库)
    ├── Document (文档) 1:N
    │   └── DocumentChunk (文档块) 1:N
    ├── ChatSession (对话会话) 1:N
    │   └── ChatMessage (消息) 1:N
    └── VectorStore (向量表) 1:1

LlmModel (LLM模型) ──── ChatSession (对话会话) N:1
PromptTemplate (提示词模板) ──── ChatMessage (消息) N:1
```

### 数据库表结构

| 表名 | 描述 | 主要字段 |
|------|------|----------|
| `ai_knowledge_base` | 知识库配置 | kb_id, name, embedding_model, chunk_size, retrieval_top_k |
| `ai_document` | 文档信息 | document_id, kb_id, file_name, process_status, chunk_count |
| `ai_document_chunk` | 文档分段 | chunk_id, document_id, content, vector_id, chunk_index |
| `ai_llm_model` | LLM模型配置 | provider, model_name, api_key_encrypted, base_url |
| `ai_chat_session` | 对话会话 | session_id, kb_id, llm_model_id, message_count |
| `ai_chat_message` | 对话消息 | message_id, session_id, role, content, token_count |
| `ai_prompt_template` | 提示词模板 | template_id, name, content, variables |
| `ai_vector_store_*` | 向量存储表 | vector_id, embedding, metadata (动态表名) |

## 配置指南

### 基础配置
```yaml
lambda:
  fusion:
    ai:
      # 文档存储配置
      document:
        storage-type: LOCAL                    # 存储类型: LOCAL/OSS
        base-path: /data/ai-documents         # 本地存储路径
        max-file-size: 10485760              # 最大文件大小(10MB)
      
      # Embedding模型配置
      embedding:
        provider: openai                      # 提供商: openai
        api-key: ${AI_OPENAI_API_KEY}        # API密钥
        model-name: text-embedding-3-small   # 模型名称
        base-url: https://api.openai.com     # API地址
        dimension: 1536                      # 向量维度
      
      # 对话模型配置
      chat:
        provider: openai                      # 提供商: openai/ollama
        api-key: ${AI_OPENAI_API_KEY}        # API密钥
        model-name: gpt-4o-mini              # 模型名称
        base-url: https://api.openai.com     # API地址
        temperature: 0.7                     # 温度参数
      
      # 文档分段配置
      document-chunk:
        default-chunk-size: 500              # 默认分块大小(tokens)
        default-chunk-overlap: 50            # 默认重叠大小(tokens)
        max-file-size: 10485760             # 最大文件大小
        batch-size: 100                     # 批处理大小
```

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lambda_fusion
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
  
  # Redis配置
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

### PostgreSQL扩展安装
```sql
-- 安装必要扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 验证安装
SELECT * FROM pg_extension WHERE extname IN ('vector', 'uuid-ossp', 'pg_trgm');
```

## 快速开始

### 1. 环境准备
- JDK 21+
- PostgreSQL 12+ (已安装pgvector扩展)
- Redis 5.0+
- OpenAI API Key 或 Ollama部署

### 2. 项目集成
```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-ai</artifactId>
    <version>2025.1.1-SNAPSHOT</version>
</dependency>
```

### 3. 启动应用
```java
@SpringBootApplication
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
```

### 4. 使用示例

#### 创建知识库
```java
@Autowired
private KnowledgeBaseService knowledgeBaseService;

CreateKnowledgeBase createKB = new CreateKnowledgeBase();
createKB.setName("技术文档库");
createKB.setDescription("存储技术文档和API说明");
createKB.setEmbeddingModel("text-embedding-3-small");
createKB.setEmbeddingDimension(1536);
createKB.setTenantId(1L);
createKB.setOwnerUserId(1L);

KnowledgeBase kb = knowledgeBaseService.createKnowledgeBase(createKB);
```

#### 上传文档
```java
@Autowired
private DocumentService documentService;

// 上传文档文件
MultipartFile file = ...; // 从前端获取
Document doc = documentService.uploadDocument(kb.getId(), file, userId);

// 文档会自动异步处理：解析 -> 分段 -> 向量化 -> 存储
```

#### 智能问答
```java
@Autowired
private RagService ragService;

// 执行RAG检索和生成
RagResult result = ragService.chat(
    "如何使用Spring Boot？",  // 用户问题
    kb.getId(),              // 知识库ID
    llmModelId              // LLM模型ID
);

System.out.println("答案: " + result.getAnswer());
System.out.println("引用文档: " + result.getRetrievedChunks().size());
```

## 错误处理

### 错误码规范
AI模块使用30000-39999范围的错误码：

| 错误码范围 | 模块 | 示例 |
|-----------|------|------|
| 30001-30099 | 知识库 | 30001: 知识库不存在 |
| 30101-30199 | 文档 | 30102: 文件大小超限 |
| 30201-30299 | 会话 | 30201: 会话不存在 |
| 30301-30399 | 消息 | 30301: 消息发送失败 |
| 30401-30499 | LLM模型 | 30401: 模型配置无效 |
| 30501-30599 | RAG/向量 | 30501: 向量检索失败 |

### 异常处理示例
```java
try {
    Document doc = documentService.uploadDocument(kbId, file, userId);
} catch (AiBusinessException e) {
    if (e.getErrorCode() == AiErrorCode.FILE_SIZE_EXCEEDED) {
        // 处理文件过大错误
    }
}
```

## 性能优化

### 缓存策略
- **模型实例缓存**: Caffeine缓存LLM模型实例(1小时TTL)
- **向量检索缓存**: Redis缓存热点查询结果
- **配置缓存**: 知识库配置本地缓存

### 异步处理
- **文档处理**: 使用@Async异步处理文档解析和向量化
- **批量操作**: 支持批量文档上传和处理
- **流式响应**: SSE支持实时流式对话

### 数据库优化
- **索引策略**: 向量表使用IVFFlat索引
- **分区表**: 大量数据时支持按时间分区
- **连接池**: HikariCP连接池优化

## 监控与运维

### 健康检查
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

### 关键指标
- 文档处理成功率
- 向量检索响应时间
- LLM调用成功率和延迟
- 知识库存储使用量

### 日志配置
```yaml
logging:
  level:
    com.lambda.fusion.ai: DEBUG
    dev.langchain4j: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 扩展开发

### 自定义文档处理器
```java
@Component
public class CustomDocumentProcessor implements DocumentProcessor {
    @Override
    public List<TextSegment> process(Document document) {
        // 自定义文档处理逻辑
        return segments;
    }
}
```

### 自定义LLM提供商
```java
@Component
public class CustomLlmProvider implements LlmProvider {
    @Override
    public ChatModel createChatModel(LlmModelEntity config) {
        // 实现自定义LLM集成
        return chatModel;
    }
}
```