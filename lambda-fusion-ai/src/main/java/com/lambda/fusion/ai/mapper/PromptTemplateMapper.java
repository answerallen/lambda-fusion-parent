package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateEntity> {
    List<PromptTemplateEntity> listByCategory(@Param("category") String category);

    List<PromptTemplateEntity> listSystemTemplates();
}
