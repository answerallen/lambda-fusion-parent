package com.lambda.fusion.core.base.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

public abstract class BaseServiceImpl<E, V, M extends BaseMapper<E>> extends ServiceImpl<M, E> {}
