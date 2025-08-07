package com.lambda.fusion.auth.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.auth.user.domain.OnlineLog;
import com.lambda.fusion.auth.user.mapper.UserOnlineLogMapper;
import com.lambda.fusion.auth.user.service.UserOnlineLogService;
import org.springframework.stereotype.Service;

@Service
public class UserOnlineLogServiceImpl extends ServiceImpl<UserOnlineLogMapper, OnlineLog>
        implements UserOnlineLogService {}
