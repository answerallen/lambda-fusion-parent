package com.lambda.fusion.core.base.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.convert.BaseConverter;
import com.lambda.cloud.core.convert.ConverterResolver;

import java.io.Serializable;
import java.util.List;

/**
 * BaseServiceImpl
 *
 * @param <E> Entity
 * @param <V> VO
 * @param <M> Mapper
 */
@SuppressWarnings("unused")
public abstract class BaseServiceImpl<E, V, M extends BaseMapper<E>> extends ServiceImpl<M, E> {

    private BaseConverter<E, V> converter() {
        return ConverterResolver.getConverter(getEntityClass());
    }

    public List<V> toVO(List<E> entity) {
        return converter().convertToList(entity);
    }

    public V toVO(E entity) {
        return converter().convertTo(entity);
    }

    public IPage<V> pageForVO(IPage<E> page, Wrapper<E> queryWrapper) {
        IPage<E> entityPage = super.page(page, queryWrapper);
        return convertPageToVO(entityPage);
    }

    public IPage<V> pageForVO(IPage<E> page) {
        IPage<E> entityPage = super.page(page);
        return convertPageToVO(entityPage);
    }

    public List<V> listForVO(Wrapper<E> queryWrapper) {
        List<E> entityList = super.list(queryWrapper);
        return convertListToVO(entityList);
    }

    public List<V> listForVO() {
        List<E> entityList = super.list();
        return convertListToVO(entityList);
    }

    public V getForVO(Wrapper<E> queryWrapper) {
        E entity = super.getOne(queryWrapper);
        return toVO(entity);
    }

    public V getByIdForVO(Serializable id) {
        E entity = super.getById(id);
        return toVO(entity);
    }

    private IPage<V> convertPageToVO(IPage<E> entityPage) {
        IPage<V> voPage = PageDTO.of(entityPage.getCurrent(), entityPage.getSize(), entityPage.getSize());
        voPage.setRecords(toVO(entityPage.getRecords()));
        return voPage;
    }

    private List<V> convertListToVO(List<E> entityList) {
        return toVO(entityList);
    }
}
