package com.lambda.fusion.upload.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.upload.model.AttachmentGroupEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AttachmentGroupMapper extends BaseMapper<AttachmentGroupEntity> {}
