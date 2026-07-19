package com.lambda.fusion.ai.agent.runtime;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 向量存储删除操作（取代自研 {@code VectorRepository} 的删除职责）。
 *
 * <p>AgentScope {@code SimpleKnowledge}/{@code PgVectorStore} 的 {@code VDBStoreBase.delete(String)}
 * 仅支持按单 id 删，无 delete-by-doc / delete-by-kb。但 PgVectorStore 建表有 **{@code doc_id} 列（带索引）**
 * （spike 核实 {@code CREATE TABLE ... doc_id VARCHAR(256) ...} + {@code idx_<table>_doc_id}），故直接 SQL：
 * <ul>
 *   <li>{@link #deleteByDocument}：{@code DELETE FROM public."<table>" WHERE doc_id = ?}（删整篇文档向量）；</li>
 *   <li>{@link #deleteAllForKb}：{@code DROP TABLE IF EXISTS public."<table>"}（删知识库向量表）。</li>
 * </ul>
 * 表名取自 {@link KnowledgeBaseEntity#getVectorTableName()}（与 {@link KnowledgeFactory} 建表一致，schema=public）。
 * 经 ai-postgres {@link DataSource}（{@link DynamicDataSourceService}）执行。
 *
 * <p>注：ingestion 由 {@link KnowledgeFactory} 构造的 {@code SimpleKnowledge.addDocuments} 写入 doc_id
 * （DocumentMetadata.docId = 文档ID，在 DocumentProcessor 装配时钉住），故此处按 doc_id 删除可命中。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreOps {

    private static final String SCHEMA = "public";

    private final DynamicDataSourceService dynamicDataSourceService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiProperties aiProperties;

    /** 删除指定文档的全部向量（按 doc_id）。 */
    public void deleteByDocument(String kbId, String documentId) {
        String table = resolveTableName(kbId);
        if (table == null) {
            return;
        }
        String sql = "DELETE FROM " + SCHEMA + ".\"" + table + "\" WHERE doc_id = ?";
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, documentId);
            int rows = ps.executeUpdate();
            log.info("VectorStoreOps: 删除文档向量 kbId={} docId={} rows={}", kbId, documentId, rows);
        } catch (SQLException e) {
            log.error("VectorStoreOps: deleteByDocument 失败 kbId={} docId={}: {}", kbId, documentId, e.getMessage());
        }
    }

    /** 删除知识库的整张向量表（删 KB 时调用）。 */
    public void deleteAllForKb(String kbId) {
        String table = resolveTableName(kbId);
        if (table == null) {
            return;
        }
        String sql = "DROP TABLE IF EXISTS " + SCHEMA + ".\"" + table + "\"";
        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
            log.info("VectorStoreOps: 删除知识库向量表 kbId={} table={}", kbId, table);
        } catch (SQLException e) {
            log.error("VectorStoreOps: deleteAllForKb 失败 kbId={}: {}", kbId, e.getMessage());
        }
    }

    private String resolveTableName(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            return null;
        }
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || !StringUtils.hasText(kb.getVectorTableName())) {
            log.debug("VectorStoreOps: 知识库无 vectorTableName，跳过 kbId={}", kbId);
            return null;
        }
        return kb.getVectorTableName();
    }

    private Connection openConnection() throws SQLException {
        DataSource ds = dynamicDataSourceService.getDataSource(aiProperties.getDataSource().getName());
        if (ds == null) {
            throw new SQLException("ai-postgres 数据源不可用: " + aiProperties.getDataSource().getName());
        }
        return ds.getConnection();
    }
}
