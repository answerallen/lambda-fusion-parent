package com.lambda.fusion.ai.apps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.apps.model.entity.AppConfigAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用配置变更审计 Mapper。仅插入与按应用查询历史，不提供更新/删除——审计为 append-only。
 *
 * @author Jin
 */
@Mapper
public interface AppConfigAuditMapper extends BaseMapper<AppConfigAuditEntity> {}
