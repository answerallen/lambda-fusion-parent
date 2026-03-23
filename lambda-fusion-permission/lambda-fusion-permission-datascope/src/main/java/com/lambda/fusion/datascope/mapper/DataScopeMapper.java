package com.lambda.fusion.datascope.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.datascope.model.DataScopeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataScopeMapper extends BaseMapper<DataScopeEntity> {

    /**
     * 批量插入
     */
    void batchInsert(@Param("list") List<DataScopeEntity> list);

    void deleteByTarget(@Param("targetId") String targetId, @Param("targetType") String targetType, @Param("domainType") Integer domainType);

    List<DataScopeEntity> selectByTypeAndId(@Param("domainType") Integer domainType, @Param("id") String id);

    void deleteByTypeAndId(@Param("domainType") Integer domainType, @Param("id") String id);

    void deleteByTypeAndIdAndOwner(
            @Param("domainType") Integer domainType,
            @Param("id") String id,
            @Param("targetId") String targetId,
            @Param("targetType") String targetType);
}
