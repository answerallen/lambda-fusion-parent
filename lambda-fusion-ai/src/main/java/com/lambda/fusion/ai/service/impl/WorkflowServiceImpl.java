package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.mapper.WorkflowMapper;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, WorkflowEntity> implements WorkflowService {}
