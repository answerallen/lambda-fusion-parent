package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.PgVectorStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 向量存储删除操作。
 *
 * <p>AgentScope {@code SimpleKnowledge}/{@code PgVectorStore} 的 {@code VDBStoreBase.delete(String)}
 * 仅支持按单 id 删，无 delete-by-doc / delete-by-kb（spike §8a 已认定的上游 API 缺口）。但
 * {@code PgVectorStore} 建表含 {@code doc_id} 列（带索引），故直接 SQL：
 *
 * <ul>
 *   <li>{@link #deleteByDocument}：{@code DELETE FROM <schema>."<table>" WHERE doc_id = ?}（删整篇文档向量）；</li>
 *   <li>{@link #deleteAllForKb}：{@code DROP TABLE IF EXISTS <schema>."<table>"}（删知识库向量表），
 *       删后失效 {@link KnowledgeFactory} 缓存（关闭已无表的 store）。</li>
 * </ul>
 *
 * <p>连接复用 {@link KnowledgeFactory} 缓存的 {@link PgVectorStore#getConnection()}（与检索同一 store
 * 实例、同一连接来源），不再独立开连接。schema/table 取自 store 实例（与建表一致）。
 *
 * <p><b>版本耦合</b>：{@code doc_id} 列名依赖 {@code PgVectorStore} 2.0.0 的内部表结构，升级
 * AgentScope 版本时需复核。上游若补齐 delete-by-doc API（Phase 4 跟进 issue），本类可退役。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreOps {

    private final KnowledgeFactory knowledgeFactory;

    /** 删除指定文档的全部向量（按 doc_id）。 */
    public void deleteByDocument(String kbId, String documentId) {
        PgVectorStore store = resolveStore(kbId);
        if (store == null) {
            return;
        }
        String sql = "DELETE FROM " + store.getSchema() + ".\"" + store.getTableName() + "\" WHERE doc_id = ?";
        try (Connection c = store.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, documentId);
            int rows = ps.executeUpdate();
            log.info("VectorStoreOps: 删除文档向量 kbId={} docId={} rows={}", kbId, documentId, rows);
        } catch (Exception e) {
            log.error("VectorStoreOps: deleteByDocument 失败 kbId={} docId={}: {}", kbId, documentId, e.getMessage());
        }
    }

    /** 删除知识库的整张向量表（删 KB 时调用），并失效缓存的 store。 */
    public void deleteAllForKb(String kbId) {
        PgVectorStore store = resolveStore(kbId);
        if (store == null) {
            return;
        }
        String sql = "DROP TABLE IF EXISTS " + store.getSchema() + ".\"" + store.getTableName() + "\"";
        try (Connection c = store.getConnection();
                Statement st = c.createStatement()) {
            st.execute(sql);
            log.info("VectorStoreOps: 删除知识库向量表 kbId={} table={}", kbId, store.getTableName());
            knowledgeFactory.invalidate(kbId);
        } catch (Exception e) {
            log.error("VectorStoreOps: deleteAllForKb 失败 kbId={}: {}", kbId, e.getMessage());
        }
    }

    /** 经 {@link KnowledgeFactory} 缓存解析 KB 的 {@link PgVectorStore}；不可用/非 PG store 返回 null。 */
    private PgVectorStore resolveStore(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            return null;
        }
        try {
            SimpleKnowledge knowledge = knowledgeFactory.get(kbId);
            if (knowledge == null || !(knowledge.getEmbeddingStore() instanceof PgVectorStore store)) {
                log.debug("VectorStoreOps: 知识库无 PgVectorStore，跳过 kbId={}", kbId);
                return null;
            }
            return store;
        } catch (Exception e) {
            log.warn("VectorStoreOps: 装配向量库失败，跳过 kbId={}: {}", kbId, e.getMessage());
            return null;
        }
    }
}
