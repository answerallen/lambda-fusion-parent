package com.lambda.fusion.core.base.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseServiceImpl<E, V, M extends BaseMapper<E>> extends ServiceImpl<M, E> {

    protected void validEntityBeforeSave(E entity) {}

    protected abstract V entityVO(E entity);

    protected List<V> listVO(List<E> list) {
        return list.stream().map(this::entityVO).collect(Collectors.toList());
    }

    protected Page<V> pageVO(Page<E> pages) {
        List<V> records = this.listVO(pages.getRecords());
        Page<V> pageVo;
        pageVo = new Page<>(pages.getCurrent(), pages.getSize(), pages.getTotal());
        pageVo.setRecords(records);
        return pageVo;
    }
}
