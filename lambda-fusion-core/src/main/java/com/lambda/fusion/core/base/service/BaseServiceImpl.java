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
 * BaseServiceImpl is an abstract class that extends ServiceImpl and provides common functionality for converting
 * entities to value objects (VO) and vice versa. It uses a generic type parameter E for the entity, V for the value
 * object, and M for the mapper which must extend BaseMapper.
 *
 * <p>This class includes methods for converting single entities or lists of entities to their corresponding value
 * objects, as well as methods for fetching paginated or non-paginated lists of value objects based on query conditions.
 * The conversion between entities and value objects is handled by a converter, which is resolved using the
 * ConverterResolver.
 */
@SuppressWarnings("unused")
public abstract class BaseServiceImpl<E, V, M extends BaseMapper<E>> extends ServiceImpl<M, E> {

    private BaseConverter<E, V> converter() {
        return ConverterResolver.getConverter(getEntityClass());
    }

    /**
     * Converts a list of entities to a list of value objects (VO).
     *
     * @param entity the list of entities to be converted
     * @return a list of value objects corresponding to the input entities
     */
    public List<V> toVO(List<E> entity) {
        return converter().convertToList(entity);
    }

    /**
     * Converts an entity to a value object (VO).
     *
     * @param entity the entity to be converted
     * @return the value object corresponding to the input entity
     */
    public V toVO(E entity) {
        return converter().convertTo(entity);
    }

    /**
     * Retrieves a page of value objects (VO) based on the provided page and query wrapper.
     *
     * @param page the page object containing pagination information
     * @param queryWrapper the wrapper used for constructing the query
     * @return a page of value objects corresponding to the input entities
     */
    public IPage<V> pageForVO(IPage<E> page, Wrapper<E> queryWrapper) {
        IPage<E> entityPage = super.page(page, queryWrapper);
        return convertPageToVO(entityPage);
    }

    /**
     * Retrieves a page of value objects (VO) based on the provided page.
     *
     * @param page the page object containing pagination information
     * @return a page of value objects corresponding to the input entities
     */
    public IPage<V> pageForVO(IPage<E> page) {
        IPage<E> entityPage = super.page(page);
        return convertPageToVO(entityPage);
    }

    /**
     * Retrieves a list of value objects (VO) based on the provided query wrapper.
     *
     * @param queryWrapper the wrapper used for constructing the query
     * @return a list of value objects corresponding to the input entities
     */
    public List<V> listForVO(Wrapper<E> queryWrapper) {
        List<E> entityList = super.list(queryWrapper);
        return convertListToVO(entityList);
    }

    /**
     * Retrieves a list of value objects (VO) for all entities.
     *
     * @return a list of value objects corresponding to all entities
     */
    public List<V> listForVO() {
        List<E> entityList = super.list();
        return convertListToVO(entityList);
    }

    /**
     * Retrieves a value object (VO) for the entity that matches the given query wrapper.
     *
     * @param queryWrapper the wrapper used for constructing the query
     * @return the value object corresponding to the found entity, or null if no matching entity is found
     */
    public V getForVO(Wrapper<E> queryWrapper) {
        E entity = super.getOne(queryWrapper);
        return toVO(entity);
    }

    /**
     * Retrieves a value object (VO) for the entity with the specified ID.
     *
     * @param id the ID of the entity to retrieve
     * @return the value object corresponding to the found entity, or null if no matching entity is found
     */
    public V getByIdForVO(Serializable id) {
        E entity = super.getById(id);
        return toVO(entity);
    }

    /**
     * Converts a page of entities to a page of value objects (VO).
     *
     * @param entityPage the page of entities to be converted
     * @return a page of value objects corresponding to the input entities
     */
    private IPage<V> convertPageToVO(IPage<E> entityPage) {
        IPage<V> voPage = PageDTO.of(entityPage.getCurrent(), entityPage.getSize(), entityPage.getSize());
        voPage.setRecords(toVO(entityPage.getRecords()));
        return voPage;
    }

    /**
     * Converts a list of entities to a list of value objects (VO) using the internal conversion mechanism.
     *
     * @param entityList the list of entities to be converted
     * @return a list of value objects corresponding to the input entities
     */
    private List<V> convertListToVO(List<E> entityList) {
        return toVO(entityList);
    }
}
