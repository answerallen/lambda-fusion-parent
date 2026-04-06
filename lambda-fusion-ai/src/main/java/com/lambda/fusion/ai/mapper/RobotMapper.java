package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.RobotEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("@aiProperties.dataSource.name")
public interface RobotMapper extends BaseMapper<RobotEntity> {}
