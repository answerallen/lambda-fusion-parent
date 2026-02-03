package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.entity.DocumentChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档块 Mapper接口
 *
 * @author Jin
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    /**
     * 根据文档ID查询文档块列表
     *
     * @param documentId 文档ID
     * @return 文档块列表(按chunk_index排序)
     */
    List<DocumentChunkEntity> listByDocumentId(@Param("documentId") Long documentId);

    /**
     * 根据知识库ID查询文档块列表
     *
     * @param kbId 知识库ID
     * @return 文档块列表
     */
    List<DocumentChunkEntity> listByKbId(@Param("kbId") Long kbId);

    /**
     * 根据vectorId查询文档块
     *
     * @param vectorId 向量ID
     * @return 文档块实体
     */
    DocumentChunkEntity selectByVectorId(@Param("vectorId") String vectorId);

    /**
     * 批量插入文档块
     *
     * @param chunkList 文档块列表
     * @return 插入数量
     */
    int batchInsert(@Param("list") List<DocumentChunkEntity> chunkList);
}
