package com.lamuda.cloud.scaffold.dict.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * DictSqlMapper
 *
 * @author Jin
 */
@Mapper
public interface DictSqlMapper {

    /**
     * SQL查询
     *
     * @param sql 查询SQL
     * @return 列表
     */
    @Select("${sql}")
    List<LinkedHashMap<String, Object>> applySql(@Param("sql") String sql);
}
