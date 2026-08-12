package com.lambda.fusion.authority.application.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.fusion.authority.application.mapper.ApplicationMapper;
import com.lambda.fusion.authority.application.model.ApplicationEntity;
import com.lambda.fusion.authority.application.service.ApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, ApplicationEntity>
        implements ApplicationService {}
