package com.lambda.fusion.authority.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.authority.user.domain.OnlineLog;
import com.lambda.fusion.authority.user.mapper.UserOnlineLogMapper;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import org.springframework.stereotype.Service;

@Service
public class UserOnlineLogServiceImpl extends ServiceImpl<UserOnlineLogMapper, OnlineLog>
        implements UserOnlineLogService {}
