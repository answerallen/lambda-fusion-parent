package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.entity.PromptTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateEntity> {
    List<PromptTemplateEntity> listByCategory(@Param("category") String category);

    List<PromptTemplateEntity> listSystemTemplates();
}
